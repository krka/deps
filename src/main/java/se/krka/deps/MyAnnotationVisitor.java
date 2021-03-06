package se.krka.deps;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

class MyAnnotationVisitor extends AnnotationVisitor {
  private final ArtifactContainerBuilder artifactContainer;

  MyAnnotationVisitor(ArtifactContainerBuilder artifactContainer) {
    super(Opcodes.ASM7);
    this.artifactContainer = artifactContainer;
  }

  @Override
  public void visit(String name, Object value) {
    super.visit(name, value);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    artifactContainer.addDescriptor(descriptor);
    super.visitEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    artifactContainer.addDescriptor(descriptor);
    return this;
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    return this;
  }

}
