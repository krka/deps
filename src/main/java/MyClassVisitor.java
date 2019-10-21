import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ASM7;

class MyClassVisitor extends ClassVisitor {
  private final MyMethodVisitor myMethodVisitor;
  private final ArtifactContainer artifactContainer;

  MyClassVisitor(ArtifactContainer artifactContainer) {
    super(ASM7);
    myMethodVisitor = new MyMethodVisitor(artifactContainer);
    this.artifactContainer = artifactContainer;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    artifactContainer.addDefinition(name);

    addClass(superName);
    for (String anInterface : interfaces) {
      addClass(anInterface);
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    artifactContainer.addDefinition(name);
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    artifactContainer.addDescriptor(descriptor);
    return null;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    artifactContainer.addDescriptor(descriptor);
    return myMethodVisitor;
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }

  void addClass(String className) {
    artifactContainer.addClass(className);
  }
}
