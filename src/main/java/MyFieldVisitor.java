import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

class MyFieldVisitor extends FieldVisitor {
  private final ArtifactContainer artifactContainer;
  private final MyAnnotationVisitor annotationVisitor;

  public MyFieldVisitor(ArtifactContainer artifactContainer, MyAnnotationVisitor annotationVisitor) {
    super(Opcodes.ASM7);
    this.artifactContainer = artifactContainer;
    this.annotationVisitor = annotationVisitor;
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

  @Override
  public void visitAttribute(Attribute attribute) {
    super.visitAttribute(attribute);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }
}
