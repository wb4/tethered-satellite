import java.awt.image.BufferedImage;

class PhysicsObject {
  Vec2D position = new Vec2D();
  Vec2D velocity = new Vec2D();
  double mass = 1.0;

  double angleRad = 0.0;
  double angularSpeed = 0.0;
  double momentOfInertia = 1.0;

  double radius = 1.0;
  BufferedImage image = null;

  Vec2D hookUplink = new Vec2D();
  Vec2D hookDownlink = new Vec2D();

  PhysicsObject downlinkObject = null;

  double tetherMaxLength = 1.0;

  PhysicsObject() {}

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

  public Vec2D hookUplinkWorldCoords() {
    return localToWorldCoords(hookUplink);
  }

  public Vec2D hookDownlinkWorldCoords() {
    return localToWorldCoords(hookDownlink);
  }

  public Vec2D localToWorldCoords(Vec2D vec) {
    return vec.rotate(angleRad).add(position);
  }

  public PhysicsObject downlinkObject() {
    return downlinkObject;
  }

  public double tetherMaxLength() {
    return tetherMaxLength;
  }

  public void setTetherMaxLength(double tetherMaxLength) {
    this.tetherMaxLength = tetherMaxLength;
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

class PhysicsObjectBuilder {
  private PhysicsObject o = new PhysicsObject();

  public PhysicsObjectBuilder position(Vec2D position) {
    o.position = position;
    return this;
  }

  public PhysicsObjectBuilder velocity(Vec2D velocity) {
    o.velocity = velocity;
    return this;
  }

  public PhysicsObjectBuilder mass(double mass) {
    o.mass = mass;
    return this;
  }

  public PhysicsObjectBuilder angleRad(double angleRad) {
    o.angleRad = angleRad;
    return this;
  }

  public PhysicsObjectBuilder angularSpeed(double angularSpeed) {
    o.angularSpeed = angularSpeed;
    return this;
  }

  public PhysicsObjectBuilder momentOfInertia(double momentOfInertia) {
    o.momentOfInertia = momentOfInertia;
    return this;
  }

  public PhysicsObjectBuilder radius(double radius) {
    o.radius = radius;
    return this;
  }

  public PhysicsObjectBuilder image(BufferedImage image) {
    o.image = image;
    return this;
  }

  public PhysicsObjectBuilder hookUplink(Vec2D hookUplink) {
    o.hookUplink = hookUplink;
    return this;
  }

  public PhysicsObjectBuilder hookDownlink(Vec2D hookDownlink) {
    o.hookDownlink = hookDownlink;
    return this;
  }

  public PhysicsObjectBuilder downlinkTo(PhysicsObject downlinkObject) {
    o.downlinkObject = downlinkObject;
    return this;
  }

  public PhysicsObject build() {
    if (o.downlinkObject != null) {
      o.tetherMaxLength =
          o.hookDownlinkWorldCoords().distanceTo(o.downlinkObject.hookUplinkWorldCoords());
    }
    return o;
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
