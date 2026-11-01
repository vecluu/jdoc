package io.github.vecluu.jdoc.source;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import io.github.vecluu.jdoc.javadoc.DocCommentRenderer;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * Walks one parsed compilation unit and builds {@link ParsedType}s and {@link ParsedMember}s: visibility (including
 * the implicit rules for interfaces, annotations, enums and records), modifiers, supertypes, rendered signatures,
 * parameters, deprecation, docs and source ranges, plus implicit members. Local and anonymous classes are skipped.
 * Single use.
 */
final class CompilationUnitExtractor {

    private static final Set<String> DEPRECATED = Set.of("Deprecated", "java.lang.Deprecated");

    private final JavaSource source;
    private final CompilationUnitTree unit;
    private final TreePath unitPath;
    private final DocTrees docTrees;
    private final DocCommentRenderer renderer;
    private final SourceRanges ranges;
    private final TypeRenderer types;
    private final SignatureFormat signatures = new SignatureFormat();
    private final ImplicitMembers implicitMembers;
    private final String packageName;
    private final List<ParsedType> result = new ArrayList<>();

    CompilationUnitExtractor(JavaSource source, CompilationUnitTree unit, DocTrees docTrees,
                             DocCommentRenderer renderer, KnownTypes knownTypes) {
        this.source = source;
        this.unit = unit;
        this.unitPath = new TreePath(unit);
        this.docTrees = docTrees;
        this.renderer = renderer;
        this.ranges = new SourceRanges(unit, docTrees.getSourcePositions(), source.text());
        this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        this.types = new TypeRenderer(resolver(knownTypes));
        this.implicitMembers = new ImplicitMembers(signatures);
    }

    ParsedSourceFile extract(List<String> problems) {
        for (Tree declaration : unit.getTypeDecls()) {
            if (declaration instanceof ClassTree type) {
                visitType(type, new TreePath(unitPath, type), Optional.empty(), TypeScope.fileLevel());
            }
        }
        Optional<RenderedDoc> packageDoc = Optional.empty();
        Optional<SourceRange> packageDocRange = Optional.empty();
        PackageTree packageTree = unit.getPackage();
        if (packageTree != null && source.fileName().equals("package-info.java")) {
            DocCommentTree comment = docTrees.getDocCommentTree(new TreePath(unitPath, packageTree));
            if (comment != null) {
                packageDoc = Optional.of(renderer.render(comment));
                packageDocRange = ranges.docComment(packageTree);
            }
        }
        return new ParsedSourceFile(source.path(), packageName, packageDoc, packageDocRange, result, problems);
    }

    private TypeNameResolver resolver(KnownTypes knownTypes) {
        Set<TypeName> fileTypes = new HashSet<>();
        for (Tree declaration : unit.getTypeDecls()) {
            if (declaration instanceof ClassTree type) {
                collectTypeNames(type, TypeName.of(packageName, List.of(type.getSimpleName().toString())),
                        fileTypes);
            }
        }
        List<String> single = new ArrayList<>();
        List<String> onDemand = new ArrayList<>();
        for (ImportTree importTree : unit.getImports()) {
            if (importTree.isStatic() || importTree.isModule()) {
                continue;
            }
            String imported = TypeRenderer.dottedName(importTree.getQualifiedIdentifier());
            if (imported.endsWith(".*")) {
                onDemand.add(imported.substring(0, imported.length() - 2));
            } else {
                single.add(imported);
            }
        }
        return new TypeNameResolver(packageName, fileTypes, single, onDemand, knownTypes);
    }

    private static void collectTypeNames(ClassTree type, TypeName name, Set<TypeName> names) {
        names.add(name);
        for (Tree member : type.getMembers()) {
            if (member instanceof ClassTree nested) {
                collectTypeNames(nested, name.nested(nested.getSimpleName().toString()), names);
            }
        }
    }

    private void visitType(ClassTree tree, TreePath path, Optional<TypeContext> outer, TypeScope outerScope) {
        String simpleName = tree.getSimpleName().toString();
        if (simpleName.isEmpty()) {
            return;
        }
        TypeName name = outer.map(o -> o.name().nested(simpleName))
                .orElseGet(() -> TypeName.of(packageName, List.of(simpleName)));
        TypeKind kind = kind(tree);
        Set<Modifier> flags = tree.getModifiers().getFlags();
        boolean inInterface = outer.isPresent() && outer.get().isInterfaceLike();
        Visibility visibility = visibility(flags, inInterface);
        boolean isStatic = flags.contains(Modifier.STATIC)
                || (outer.isPresent() && (inInterface || kind != TypeKind.CLASS));
        TypeScope scope = outerScope.enter(name).withTypeParameters(tree.getTypeParameters());
        DocCommentTree comment = docTrees.getDocCommentTree(path);
        RenderedDoc doc = renderer.render(comment);
        Optional<SourceRange> docRange = comment == null ? Optional.empty() : ranges.docComment(tree);
        TypeDescriptor descriptor = TypeDescriptor.builder()
                .name(name)
                .kind(kind)
                .visibility(visibility)
                .modifiers(typeModifiers(flags, visibility, kind, isStatic))
                .typeParameters(types.typeParameters(tree.getTypeParameters(), scope))
                .supertypes(supertypes(tree, name, kind,
                        outerScope.withTypeParameters(tree.getTypeParameters())))
                .deprecated(isDeprecated(tree.getModifiers()) || renderer.hasDeprecatedTag(comment))
                .firstSentence(doc.firstSentence())
                .docSource(source.docSource())
                .entryPath(source.docSource() == DocSource.DECOMPILED ? name.classEntryPath() : source.path())
                .declarationRange(ranges.withDoc(ranges.declaration(tree), docRange))
                .build();
        TypeContext context = new TypeContext(name, kind, visibility, scope, comment);
        int index = result.size();
        result.add(null);
        List<ClassTree> nestedTypes = new ArrayList<>();
        List<ParsedMember> members = members(tree, path, context, nestedTypes);
        result.set(index, new ParsedType(descriptor, doc, members));
        TypeScope nestedScope = isStatic ? outerScope.enter(name) : scope;
        for (ClassTree nested : nestedTypes) {
            visitType(nested, new TreePath(path, nested), Optional.of(context), nestedScope);
        }
    }

    private List<ParsedMember> members(ClassTree tree, TreePath typePath, TypeContext type,
                                       List<ClassTree> nestedTypes) {
        List<ParsedMember> members = new ArrayList<>();
        List<VariableTree> components = new ArrayList<>();
        for (Tree member : tree.getMembers()) {
            TreePath path = new TreePath(typePath, member);
            switch (member) {
                case ClassTree nested -> nestedTypes.add(nested);
                case VariableTree variable when isEnumConstant(variable, type) ->
                        members.add(enumConstant(variable, path, type));
                case VariableTree variable when type.kind() == TypeKind.RECORD
                        && !variable.getModifiers().getFlags().contains(Modifier.STATIC) -> components.add(variable);
                case VariableTree variable -> members.add(field(variable, path, type));
                case MethodTree method -> members.add(method(method, path, type, components));
                default -> {
                    // initializer blocks and erroneous trees declare nothing
                }
            }
        }
        members.addAll(implicitMembers(type, members, components));
        return members;
    }

    private List<ParsedMember> implicitMembers(TypeContext type, List<ParsedMember> declared,
                                               List<VariableTree> components) {
        return switch (type.kind()) {
            case CLASS -> declared.stream().anyMatch(m -> m.descriptor().kind() == MemberKind.CONSTRUCTOR)
                    ? List.of()
                    : List.of(implicitMembers.defaultConstructor(type.name(), type.visibility()));
            case ENUM -> implicitMembers.enumMethods(type.name(), declared);
            case RECORD -> implicitMembers.recordMembers(type.name(), type.visibility(),
                    componentInfos(components, type.scope()), renderer.parameterDescriptions(type.comment()),
                    declared);
            default -> List.of();
        };
    }

    private ParsedMember enumConstant(VariableTree variable, TreePath path, TypeContext type) {
        DocCommentTree comment = docTrees.getDocCommentTree(path);
        RenderedDoc doc = renderer.render(comment);
        String name = variable.getName().toString();
        return declared(MemberDescriptor.builder()
                .name(name)
                .kind(MemberKind.ENUM_CONSTANT)
                .signature(signatures.enumConstant(name))
                .visibility(Visibility.PUBLIC)
                .isStatic(true), variable, comment, doc, type);
    }

    private ParsedMember field(VariableTree variable, TreePath path, TypeContext type) {
        DocCommentTree comment = docTrees.getDocCommentTree(path);
        RenderedDoc doc = renderer.render(comment);
        Set<Modifier> flags = variable.getModifiers().getFlags();
        boolean inInterface = type.isInterfaceLike();
        Visibility visibility = visibility(flags, inInterface);
        boolean isStatic = inInterface || flags.contains(Modifier.STATIC);
        boolean isFinal = inInterface || flags.contains(Modifier.FINAL);
        String name = variable.getName().toString();
        return declared(MemberDescriptor.builder()
                .name(name)
                .kind(MemberKind.FIELD)
                .signature(signatures.field(visibility, isStatic, isFinal,
                        types.render(variable.getType(), type.scope()), name))
                .visibility(visibility)
                .isStatic(isStatic), variable, comment, doc, type);
    }

    private ParsedMember method(MethodTree method, TreePath path, TypeContext type, List<VariableTree> components) {
        DocCommentTree comment = docTrees.getDocCommentTree(path);
        RenderedDoc doc = renderer.render(comment);
        Set<Modifier> flags = method.getModifiers().getFlags();
        boolean inInterface = type.isInterfaceLike();
        Visibility visibility = visibility(flags, inInterface);
        TypeScope scope = type.scope().withTypeParameters(method.getTypeParameters());
        String typeParameters = types.typeParameters(method.getTypeParameters(), scope);
        List<ParameterInfo> parameters = parameters(method.getParameters(), scope);
        List<String> thrown = method.getThrows().stream().map(t -> types.render(t, scope)).toList();
        boolean constructor = method.getReturnType() == null;
        if (constructor && type.kind() == TypeKind.ENUM) {
            visibility = Visibility.PRIVATE;
        }
        MemberDescriptor.MemberDescriptorBuilder builder = MemberDescriptor.builder().visibility(visibility);
        if (constructor) {
            if (type.kind() == TypeKind.RECORD) {
                parameters = recordConstructorParameters(parameters, componentInfos(components, type.scope()));
            }
            String simpleName = type.name().simpleName();
            builder.name(simpleName)
                    .kind(MemberKind.CONSTRUCTOR)
                    .signature(signatures.constructor(visibility, typeParameters, simpleName, parameters, thrown));
        } else {
            boolean isStatic = flags.contains(Modifier.STATIC);
            String name = method.getName().toString();
            builder.name(name)
                    .kind(MemberKind.METHOD)
                    .isStatic(isStatic)
                    .signature(signatures.method(visibility, isStatic, style(flags, inInterface, method),
                            typeParameters, types.render(method.getReturnType(), scope), name, parameters, thrown));
        }
        return declared(builder.parameters(parameters), method, comment, doc, type);
    }

    private ParsedMember declared(MemberDescriptor.MemberDescriptorBuilder builder, Tree tree,
                                  DocCommentTree comment, RenderedDoc doc, TypeContext type) {
        Optional<SourceRange> docRange = comment == null ? Optional.empty() : ranges.docComment(tree);
        ModifiersTree modifiers = switch (tree) {
            case VariableTree variable -> variable.getModifiers();
            case MethodTree method -> method.getModifiers();
            default -> null;
        };
        MemberDescriptor descriptor = builder
                .declaringType(type.name())
                .deprecated((modifiers != null && isDeprecated(modifiers)) || renderer.hasDeprecatedTag(comment))
                .firstSentence(doc.firstSentence())
                .range(ranges.withDoc(ranges.declaration(tree), docRange))
                .docRange(docRange)
                .build();
        return new ParsedMember(descriptor, doc, false);
    }

    private List<ParameterInfo> parameters(List<? extends VariableTree> variables, TypeScope scope) {
        List<ParameterInfo> parameters = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            parameters.add(parameter(variables.get(i), scope, i == variables.size() - 1));
        }
        return parameters;
    }

    private ParameterInfo parameter(VariableTree variable, TypeScope scope, boolean last) {
        Tree type = variable.getType();
        String name = variable.getName().toString();
        if (last && type instanceof ArrayTypeTree array && ranges.isVarargs(array.getType(), variable)) {
            return new ParameterInfo(name, types.render(array.getType(), scope) + "...",
                    types.erase(array.getType(), scope) + "[]");
        }
        return new ParameterInfo(name, types.render(type, scope), types.erase(type, scope));
    }

    private List<ParameterInfo> componentInfos(List<VariableTree> components, TypeScope scope) {
        return parameters(components, scope);
    }

    /**
     * A constructor matching the record components (the parser fills compact constructors with copies of the
     * component declarations) takes the component parameter infos, which keep the varargs form.
     */
    private static List<ParameterInfo> recordConstructorParameters(List<ParameterInfo> parameters,
                                                                   List<ParameterInfo> components) {
        if (parameters.size() != components.size()) {
            return parameters;
        }
        for (int i = 0; i < parameters.size(); i++) {
            if (!parameters.get(i).name().equals(components.get(i).name())
                    || !parameters.get(i).erasedType().equals(components.get(i).erasedType())) {
                return parameters;
            }
        }
        return components;
    }

    private static SignatureFormat.MethodStyle style(Set<Modifier> flags, boolean inInterface, MethodTree method) {
        if (flags.contains(Modifier.DEFAULT)) {
            return SignatureFormat.MethodStyle.DEFAULT;
        }
        boolean implicitAbstract = inInterface && method.getBody() == null
                && !flags.contains(Modifier.STATIC) && !flags.contains(Modifier.PRIVATE);
        return flags.contains(Modifier.ABSTRACT) || implicitAbstract
                ? SignatureFormat.MethodStyle.ABSTRACT
                : SignatureFormat.MethodStyle.PLAIN;
    }

    private static boolean isEnumConstant(VariableTree variable, TypeContext type) {
        return type.kind() == TypeKind.ENUM
                && variable.getInitializer() instanceof NewClassTree creation
                && TypeRenderer.dottedName(creation.getIdentifier()).equals(type.name().simpleName());
    }

    private static TypeKind kind(ClassTree tree) {
        return switch (tree.getKind()) {
            case INTERFACE -> TypeKind.INTERFACE;
            case ENUM -> TypeKind.ENUM;
            case RECORD -> TypeKind.RECORD;
            case ANNOTATION_TYPE -> TypeKind.ANNOTATION;
            default -> TypeKind.CLASS;
        };
    }

    private static Visibility visibility(Set<Modifier> flags, boolean inInterface) {
        if (flags.contains(Modifier.PUBLIC)) {
            return Visibility.PUBLIC;
        }
        if (flags.contains(Modifier.PROTECTED)) {
            return Visibility.PROTECTED;
        }
        if (flags.contains(Modifier.PRIVATE)) {
            return Visibility.PRIVATE;
        }
        return inInterface ? Visibility.PUBLIC : Visibility.PACKAGE;
    }

    private static String typeModifiers(Set<Modifier> flags, Visibility visibility, TypeKind kind, boolean isStatic) {
        List<String> words = new ArrayList<>();
        if (!visibility.keyword().isEmpty()) {
            words.add(visibility.keyword());
        }
        if (flags.contains(Modifier.ABSTRACT) && kind == TypeKind.CLASS) {
            words.add("abstract");
        }
        if (isStatic) {
            words.add("static");
        }
        for (Modifier modifier : List.of(Modifier.FINAL, Modifier.SEALED, Modifier.NON_SEALED, Modifier.STRICTFP)) {
            if (flags.contains(modifier)) {
                words.add(modifier.toString());
            }
        }
        return String.join(" ", words);
    }

    private List<TypeName> supertypes(ClassTree tree, TypeName name, TypeKind kind, TypeScope scope) {
        List<TypeName> result = new ArrayList<>();
        Tree extendsClause = tree.getExtendsClause();
        if (extendsClause != null) {
            result.add(resolve(extendsClause, scope));
        } else {
            implicitSuperclass(name, kind).ifPresent(result::add);
        }
        for (Tree implemented : tree.getImplementsClause()) {
            result.add(resolve(implemented, scope));
        }
        if (kind == TypeKind.ANNOTATION) {
            result.add(TypeName.of("java.lang.annotation.Annotation"));
        }
        return result;
    }

    private static Optional<TypeName> implicitSuperclass(TypeName name, TypeKind kind) {
        return switch (kind) {
            case CLASS -> name.binaryName().equals("java.lang.Object")
                    ? Optional.empty()
                    : Optional.of(TypeName.of("java.lang.Object"));
            case ENUM -> Optional.of(TypeName.of("java.lang.Enum"));
            case RECORD -> Optional.of(TypeName.of("java.lang.Record"));
            default -> Optional.empty();
        };
    }

    private TypeName resolve(Tree type, TypeScope scope) {
        return types.resolve(TypeRenderer.dottedName(type), scope);
    }

    private static boolean isDeprecated(ModifiersTree modifiers) {
        for (AnnotationTree annotation : modifiers.getAnnotations()) {
            if (DEPRECATED.contains(TypeRenderer.dottedName(annotation.getAnnotationType()))) {
                return true;
            }
        }
        return false;
    }

    /** What member extraction needs to know about the declaring type. */
    private record TypeContext(TypeName name, TypeKind kind, Visibility visibility, TypeScope scope,
                               DocCommentTree comment) {
        boolean isInterfaceLike() {
            return kind == TypeKind.INTERFACE || kind == TypeKind.ANNOTATION;
        }
    }
}
