import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

public class TetherSim {

  private static final int VIEW_WIDTH = 2000;
  private static final int VIEW_HEIGHT = 2000;

  private static final double SPACE_VIEW_WIDTH = 40000.0;

  private static final double G = 1.0; // gravitational constant

  private static final double EARTH_RADIUS = 12742.0 / 2.0;
  private static final double EARTH_MASS = 100000000000.0;

  private static final double SATELLITE_RADIUS = 600.0;
  private static final double SATELLITE_MASS = 100.0;

  private static final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");
  private static final File EARTH_IMAGE_FILE = new File("earth.png");
  private static final File MAIN_SATELLITE_IMAGE_FILE = new File("satellite_main.png");

  // COLLISION_ELASTICITY should be between 0 and a little less than 1 for realistic physics.
  // 0 means collisions are completely inelastic; 1 means completely elastic.
  // But stay a little bit under 1, because of energy leakage.
  private static final double COLLISION_ELASTICITY = 0.95;
  private static final double COEFFICIENT_OF_FRICTION = 0.1;

  private static final double FPS_DESIRED = 60.0;

  private JComponent simCanvas;
  private List<PhysicsObject> physicsObjects;

  private GravitySource earthGravity;

  private Object physicsLock = new Object();

  public static void main(String[] args) {
    new TetherSim().start();
  }

  public TetherSim() {
    JFrame frame = new JFrame("TetherSim");

    BufferedImage backgroundImage = loadImageOrDie(BACKGROUND_IMAGE_FILE);
    BufferedImage earthImage = loadImageOrDie(EARTH_IMAGE_FILE);
    BufferedImage mainSatelliteImage = loadImageOrDie(MAIN_SATELLITE_IMAGE_FILE);

    PhysicsObject earth =
        new PhysicsObject(
            new Vec2D(0.0, 0.0),
            new Vec2D(0.0, 0.0),
            EARTH_MASS,
            Math.toRadians(23.5),
            0.0,
            momentOfInertiaForDisc(EARTH_MASS, EARTH_RADIUS),
            EARTH_RADIUS,
            earthImage);

    PhysicsObject satellite =
        new PhysicsObject(
            new Vec2D(0.0, 2.0 * EARTH_RADIUS),
            new Vec2D(0, 0),
            SATELLITE_MASS,
            0.0,
            0.0,
            momentOfInertiaForDisc(SATELLITE_MASS, SATELLITE_RADIUS),
            SATELLITE_RADIUS,
            mainSatelliteImage);

    Vec2D orbitImpulse = calculateOrbitalImpulse(satellite, earth).scale(0.5);
    satellite.feelImpulse(orbitImpulse);
    earth.feelImpulse(orbitImpulse.flip());

    this.physicsObjects = new ArrayList<>();
    physicsObjects.add(earth);
    physicsObjects.add(satellite);

    this.earthGravity = new GravitySource(earth);

    this.simCanvas = new SimCanvas(SPACE_VIEW_WIDTH, backgroundImage, physicsObjects, physicsLock);
    simCanvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
    frame.add(simCanvas);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }

  private BufferedImage loadImageOrDie(File imageFile) {
    BufferedImage image = null;
    try {
      image = ImageIO.read(imageFile);
    } catch (IOException exc) {
      System.err.println("error loading image " + imageFile + ": " + exc.getMessage());
      System.exit(1);
    }

    return image;
  }

  private Vec2D calculateOrbitalImpulse(PhysicsObject a, PhysicsObject b) {
    Vec2D offset = b.position().sub(a.position());

    double impulseMagnitude =
        a.mass() * b.mass() * Math.sqrt(G / ((a.mass() + b.mass()) * offset.length()));

    return offset.rotate(-Math.PI / 2.0).toLength(impulseMagnitude);
  }

  private double momentOfInertiaForDisc(double mass, double radius) {
    return 0.5 * mass * radius * radius;
  }

  public void start() {
    new Thread(() -> run(), "Physics Simulator").start();
  }

  private void run() {
    final long targetMillisPerFrame = (long) (1000.0 / FPS_DESIRED);

    long lastTickTimeMillis = System.currentTimeMillis();

    for (; ; ) {
      long startTimeMillis = System.currentTimeMillis();

      double tickSecs = (startTimeMillis - lastTickTimeMillis) / 1000.0;

      tickPhysics(tickSecs);

      simCanvas.repaint();
      lastTickTimeMillis = startTimeMillis;

      long elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
      long sleepTimeMillis = targetMillisPerFrame - elapsedTimeMillis;
      if (sleepTimeMillis > 0) {
        try {
          Thread.sleep(sleepTimeMillis);
        } catch (InterruptedException exc) {
        }
      }
    }
  }

  private void tickPhysics(double secs) {
    applyGravity(secs);

    applyCollisions(secs);
    synchronized (physicsLock) {
      applyMovement(secs);
    }
  }

  private void applyGravity(double secs) {
    for (PhysicsObject po : physicsObjects) {
      po.feelGravity(earthGravity, secs);
    }
  }

  private void applyCollisions(double secs) {
    for (int i = 0; i < physicsObjects.size() - 1; i++) {
      PhysicsObject a = physicsObjects.get(i);
      for (int j = i + 1; j < physicsObjects.size(); j++) {
        PhysicsObject b = physicsObjects.get(j);
        applyCollision(a, b, secs);
      }
    }
  }

  private void applyCollision(PhysicsObject a, PhysicsObject b, double secs) {
    // A collision happens when two objects physically overlap *while*
    // moving towards each other, so first we check for that condition.

    double minDistance = a.radius() + b.radius();
    Vec2D offset = b.position().sub(a.position());
    if (offset.lengthSquared() >= minDistance * minDistance) {
      // The objects don't overlap, so no collision.
      return;
    }

    Vec2D centerOfMassVelocity = a.momentum().add(b.momentum()).scale(1.0 / (a.mass() + b.mass()));

    Vec2D bRelativeVelocity = b.velocity().sub(centerOfMassVelocity);
    double speedDotOffset = bRelativeVelocity.dot(offset);

    if (speedDotOffset >= 0.0) {
      // The objects are not moving closer to each other, so no collision.
      return;
    }

    // We resolve the collision by taking the speed at which one of the objects
    // is moving towards the center of mass of the two-object system, then accelerating
    // the objects away from each other by the same delta-speed so that they
    // are no longer moving towards or away from each other.  Then we scale that
    // delta-speed by the collision elasticity factor and accelerate them again by
    // the result, so that they don't just stop but in fact bounce away.

    double closingSpeed = -speedDotOffset / offset.length();

    double deltaV = closingSpeed * (1.0 + COLLISION_ELASTICITY);
    double impulseMagnitude = b.mass() * deltaV;

    Vec2D impulse = offset.toLength(impulseMagnitude);

    b.feelImpulse(impulse);
    a.feelImpulse(impulse.flip());

    // Now we deal with the friction between the two bodies at their point of contact.

    Vec2D vAP = a.velocity().componentPerpendicularTo(offset);
    Vec2D vBP = b.velocity().componentPerpendicularTo(offset);

    Vec2D u = offset.rotate(Math.PI / 2.0).normalized();

    Vec2D vEA = vAP.add(u.scale(a.radius() * a.angularSpeed()));
    Vec2D vEB = vBP.sub(u.scale(b.radius() * b.angularSpeed()));

    Vec2D velRelative = vEB.sub(vEA);
    double velRelativeLen = velRelative.length();

    Vec2D frictionImpulseDir = velRelative.scale(1.0 / velRelativeLen);
    double frictionImpulseMagnitudeMax =
        Math.abs(
            1.0
                / ((1.0 / a.mass())
                    + (1.0 / b.mass())
                    + (Math.pow(a.radius(), 2) / a.momentOfInertia())
                    + (Math.pow(b.radius(), 2) / b.momentOfInertia()))
                * velRelativeLen);

    double frictionImpulseMagnitude =
        Math.min(COEFFICIENT_OF_FRICTION * impulseMagnitude, frictionImpulseMagnitudeMax);

    Vec2D frictionImpulse = frictionImpulseDir.scale(frictionImpulseMagnitude);

    a.feelImpulseAt(frictionImpulse, a.position().add(offset.toLength(a.radius())));
    b.feelImpulseAt(frictionImpulse.flip(), b.position().sub(offset.toLength(b.radius())));
  }

  private void applyMovement(double secs) {
    for (PhysicsObject po : physicsObjects) {
      po.move(secs);
    }
  }
}

class SimCanvas extends JComponent {

  private double spaceViewWidth;

  private BufferedImage backgroundImage;

  private List<PhysicsObject> physicsObjects;
  ;

  private Object physicsLock;

  public SimCanvas(
      double spaceViewWidth,
      BufferedImage backgroundImage,
      List<PhysicsObject> physicsObjects,
      Object physicsLock) {
    this.spaceViewWidth = spaceViewWidth;
    this.backgroundImage = backgroundImage;

    this.physicsObjects = physicsObjects;

    this.physicsLock = physicsLock;
  }

  protected void paintComponent(Graphics legacyG) {
    Graphics2D g = (Graphics2D) legacyG;

    drawBackground(g);

    synchronized (physicsLock) {
      drawPhysicsObjects(g);
    }
  }

  private void drawBackground(Graphics2D g) {
    int xOff = (getWidth() - backgroundImage.getWidth()) / 2;
    int yOff = (getHeight() - backgroundImage.getHeight()) / 2;

    g.drawImage(backgroundImage, null, xOff, yOff);
  }

  private void drawPhysicsObjects(Graphics2D g) {
    for (PhysicsObject po : physicsObjects) {
      drawImageInWorld(g, po.image(), po.position(), po.angleRad(), po.radius());
    }
  }

  private void drawImageInWorld(
      Graphics2D g, BufferedImage image, Vec2D position, double angleRad, double radius) {
    AffineTransform transform = new AffineTransform();

    transform.translate(getWidth() / 2, getHeight() / 2);

    double viewScale = Math.min(getWidth(), getHeight()) / spaceViewWidth;
    transform.scale(viewScale, viewScale);

    transform.translate(position.x(), -position.y());
    transform.rotate(-angleRad);
    transform.scale(2.0 * radius / image.getWidth(), 2.0 * radius / image.getHeight());
    transform.translate(-image.getWidth() / 2, -image.getHeight() / 2);

    BufferedImageOp imageOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC);

    g.drawImage(image, imageOp, 0, 0);
  }
}

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

class Vec2D {
  private double x;
  private double y;

  public Vec2D(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public Vec2D add(Vec2D other) {
    return new Vec2D(x + other.x, y + other.y);
  }

  public Vec2D sub(Vec2D other) {
    return new Vec2D(x - other.x, y - other.y);
  }

  public Vec2D scale(double s) {
    return new Vec2D(s * x, s * y);
  }

  public Vec2D normalized() {
    return scale(1.0 / length());
  }

  public Vec2D flip() {
    return scale(-1.0);
  }

  public Vec2D toLength(double newLength) {
    return scale(newLength / length());
  }

  public double dot(Vec2D other) {
    return x * other.x + y * other.y;
  }

  public double cross(Vec2D other) {
    return x * other.y - y * other.x;
  }

  public Vec2D rotate(double radians) {
    double sin = Math.sin(radians);
    double cos = Math.cos(radians);

    return new Vec2D(x * cos - y * sin, x * sin + y * cos);
  }

  public Vec2D componentParallelTo(Vec2D other) {
    return other.scale(dot(other) / other.lengthSquared());
  }

  public Vec2D componentPerpendicularTo(Vec2D other) {
    return sub(componentParallelTo(other));
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public double lengthSquared() {
    return x * x + y * y;
  }

  public String toString() {
    return "Vec2D { " + x + ", " + y + " }";
  }
}
