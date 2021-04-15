import java.awt.image.BufferedImage;

class PhysicsObject {
  private Vec2D position;
  private Vec2D velocity;
  private double mass;

  private double angleRad;
  private double angularSpeed;
  private double momentOfInertia;

  private double radius;
  private BufferedImage image;

  public PhysicsObject(
      Vec2D position,
      Vec2D velocity,
      double mass,
      double angleRad,
      double angularSpeed,
      double momentOfInertia,
      double radius,
      BufferedImage image) {
    this.position = position;
    this.velocity = velocity;
    this.mass = mass;

    this.angleRad = angleRad;
    this.angularSpeed = angularSpeed;
    this.momentOfInertia = momentOfInertia;

    this.radius = radius;
    this.image = image;
  }

  public Vec2D position() {
    return position;
  }

  public Vec2D velocity() {
    return velocity;
  }

  public double mass() {
    return mass;
  }

  public Vec2D momentum() {
    return velocity.scale(mass);
  }

  public double angleRad() {
    return angleRad;
  }

  public double angularSpeed() {
    return angularSpeed;
  }

  public double momentOfInertia() {
    return momentOfInertia;
  }

  public double radius() {
    return radius;
  }

  public BufferedImage image() {
    return image;
  }

  public void move(double secs) {
    position = position.add(velocity.scale(secs));
    angleRad += angularSpeed * secs;
    while (angleRad < 0.0) {
      angleRad += 2.0 * Math.PI;
    }
    while (angleRad >= 2.0 * Math.PI) {
      angleRad -= 2.0 * Math.PI;
    }
  }

  public void feelGravity(GravitySource source, double secs) {
    PhysicsObject other = source.physicsObject();
    if (other == this) {
      return;
    }

    Vec2D offset = other.position().sub(position);
    Vec2D normalizedDirection = offset.normalized();
    double forceMagnitude = mass * other.mass() / offset.lengthSquared();

    Vec2D force = normalizedDirection.scale(forceMagnitude);

    feelForce(force, secs);
    other.feelForce(force.flip(), secs);
  }

  public void feelForce(Vec2D force, double secs) {
    feelImpulse(force.scale(secs));
  }

  public void feelImpulse(Vec2D impulse) {
    velocity = velocity.add(impulse.scale(1.0 / mass));
  }

  public void feelImpulseAt(Vec2D impulse, Vec2D impulsePos) {
    feelImpulse(impulse);

    Vec2D momentArm = impulsePos.sub(position);
    feelAngularImpulse(momentArm.cross(impulse));
  }

  public void feelTorque(double torque, double secs) {
    feelAngularImpulse(torque * secs);
  }

  public void feelAngularImpulse(double angularImpulse) {
    angularSpeed += angularImpulse / momentOfInertia;
  }
}

class GravitySource {
  private PhysicsObject physicsObject;

  public GravitySource(PhysicsObject physicsObject) {
    this.physicsObject = physicsObject;
  }

  public PhysicsObject physicsObject() {
    return physicsObject;
  }
}
