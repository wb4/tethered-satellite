import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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

  private static final double MAIN_SATELLITE_RADIUS = 600.0;
  private static final double MAIN_SATELLITE_MASS = 100.0;

  private static final double SECONDARY_SATELLITE_RADIUS = 400.0;
  private static final double SECONDARY_SATELLITE_MASS = 70.0;

  private static final String BACKGROUND_IMAGE_FILE = "images/space_background.jpg";
  private static final String EARTH_IMAGE_FILE = "images/earth.png";
  private static final String MAIN_SATELLITE_IMAGE_FILE = "images/satellite_main.png";
  private static final String SECONDARY_SATELLITE_IMAGE_FILE = "images/satellite_secondary.png";

  // COLLISION_ELASTICITY should be between 0 and a little less than 1 for realistic physics.
  // 0 means collisions are completely inelastic; 1 means completely elastic.
  // But stay a little bit under 1, because of energy leakage.
  private static final double COLLISION_ELASTICITY = 0.95;

  private static final double TETHER_REBOUND_ELASTICITY = 0.5;

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
    BufferedImage secondarySatelliteImage = loadImageOrDie(SECONDARY_SATELLITE_IMAGE_FILE);

    PhysicsObject earth =
        new PhysicsObjectBuilder()
            .mass(EARTH_MASS)
            .angleRad(Math.toRadians(23.5))
            .momentOfInertia(momentOfInertiaForDisc(EARTH_MASS, EARTH_RADIUS))
            .radius(EARTH_RADIUS)
            .image(earthImage)
            .build();

    PhysicsObject secondarySatellite =
        new PhysicsObjectBuilder()
            .position(new Vec2D(0.0, 2.0 * EARTH_RADIUS))
            .mass(SECONDARY_SATELLITE_MASS)
            .momentOfInertia(
                momentOfInertiaForDisc(SECONDARY_SATELLITE_MASS, SECONDARY_SATELLITE_RADIUS))
            .radius(SECONDARY_SATELLITE_RADIUS)
            .image(secondarySatelliteImage)
            .hookUplink(new Vec2D(0.0, 0.90 * SECONDARY_SATELLITE_RADIUS))
            .build();

    PhysicsObject mainSatellite =
        new PhysicsObjectBuilder()
            .position(new Vec2D(0.0, 3.0 * EARTH_RADIUS))
            .mass(MAIN_SATELLITE_MASS)
            .momentOfInertia(momentOfInertiaForDisc(MAIN_SATELLITE_MASS, MAIN_SATELLITE_RADIUS))
            .radius(MAIN_SATELLITE_RADIUS)
            .image(mainSatelliteImage)
            .hookDownlink(new Vec2D(0.0, -0.5 * MAIN_SATELLITE_RADIUS))
            .downlinkTo(secondarySatellite)
            .build();

    Vec2D orbitImpulse = calculateOrbitalImpulse(mainSatellite, earth).scale(1.5);
    mainSatellite.feelImpulse(orbitImpulse);
    earth.feelImpulse(orbitImpulse.flip());

    orbitImpulse = calculateOrbitalImpulse(secondarySatellite, earth).scale(0.5);
    secondarySatellite.feelImpulse(orbitImpulse);
    earth.feelImpulse(orbitImpulse.flip());

    this.physicsObjects = new ArrayList<>();
    physicsObjects.add(earth);
    physicsObjects.add(mainSatellite);
    physicsObjects.add(secondarySatellite);

    this.earthGravity = new GravitySource(earth);

    this.simCanvas = new SimCanvas(SPACE_VIEW_WIDTH, backgroundImage, physicsObjects, physicsLock);
    simCanvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
    frame.add(simCanvas);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }

  private BufferedImage loadImageOrDie(String imageFile) {
    BufferedImage image = null;
    try {
      InputStream stream = getClass().getResourceAsStream(imageFile);
      if (stream == null) {
        System.err.println("cannot find image file: " + imageFile);
        System.exit(1);
      }
      image = ImageIO.read(stream);
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

    applyTetherRebounds(secs);
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

  private void applyTetherRebounds(double secs) {
    for (PhysicsObject po : physicsObjects) {
      if (po.downlinkObject() != null) {
        applyTetherRebound(po);
      }
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

  private void applyTetherRebound(PhysicsObject a) {
    // A tether rebound happens when the two endpoints of the tether are
    // farther apart than its maximum length *while* the endpoints are moving
    // away from each other, so we check for that first.

    PhysicsObject b = a.downlinkObject();

    Vec2D aHook = a.hookDownlinkWorldCoords();
    Vec2D bHook = b.hookUplinkWorldCoords();

    if (aHook.distanceSquaredTo(bHook) <= a.tetherMaxLength * a.tetherMaxLength) {
      // The two ends of the tether are not far enough apart to cause it to rebound.
      return;
    }

    Vec2D offset = bHook.sub(aHook);
    Vec2D offsetUnit = offset.toLength(1.0);

    Vec2D uA = aHook.sub(a.position()).rotate(Math.PI / 2.0);
    Vec2D uB = bHook.sub(b.position()).rotate(Math.PI / 2.0);

    Vec2D vHa = a.velocity().add(uA.scale(a.angularSpeed()));
    Vec2D vHb = b.velocity().add(uB.scale(b.angularSpeed()));

    double vHaP = vHa.dot(offsetUnit);
    double vHbP = vHb.dot(offsetUnit);

    if (vHbP <= vHaP) {
      // The two ends of the tether are not moving away from each other, so there
      // is no rebound.
      return;
    }

    double impulseMagnitude =
        (vHbP - vHaP)
            / (1.0 / a.mass()
                + 1.0 / b.mass()
                + Math.pow(uA.dot(offsetUnit), 2.0) / a.momentOfInertia()
                + Math.pow(uB.dot(offsetUnit), 2.0) / b.momentOfInertia());

    Vec2D impulse = offsetUnit.scale(impulseMagnitude * (1.0 + TETHER_REBOUND_ELASTICITY));

    a.feelImpulseAt(impulse, aHook);
    b.feelImpulseAt(impulse.flip(), bHook);
  }

  private void applyMovement(double secs) {
    for (PhysicsObject po : physicsObjects) {
      po.move(secs);
    }
  }
}
