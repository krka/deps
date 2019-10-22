package se.krka.deps;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

import static org.objectweb.asm.Opcodes.ASM7;

class MyClassVisitor extends ClassVisitor {
  private final ArtifactContainerBuilder artifactContainer;
  private final MyMethodVisitor methodVisitor;
  private final MyAnnotationVisitor annotationVisitor;
  private final MyFieldVisitor fieldVisitor;
  private String className;

  MyClassVisitor(ArtifactContainerBuilder artifactContainer) {
    super(ASM7);
    this.artifactContainer = artifactContainer;
    annotationVisitor = new MyAnnotationVisitor(artifactContainer);
    methodVisitor = new MyMethodVisitor(artifactContainer, annotationVisitor);
    fieldVisitor = new MyFieldVisitor(artifactContainer, annotationVisitor);
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    className = name;
    if (className.equals("module-info")) {
      // Not useful to keep this
      return;
    }
    artifactContainer.addDefinition(className);

    if (superName != null) {
      artifactContainer.addClass(superName);
    }
    for (String anInterface : interfaces) {
      artifactContainer.addClass(anInterface);
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if (outerName == null || outerName.equals(className)) {
      artifactContainer.addDefinition(name);
    }
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    artifactContainer.addDescriptor(descriptor);
    return fieldVisitor;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    artifactContainer.addDescriptor(descriptor);
    return methodVisitor;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

}
