import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

public class TetherSim {

  private static final int VIEW_WIDTH = 2000;
  private static final int VIEW_HEIGHT = 2000;

  private static final double SPACE_VIEW_WIDTH = 40000.0;

  private static final double G = 1.0; // gravitational constant

  private static final double EARTH_RADIUS = 12742.0 / 2.0;
  private static final double EARTH_MASS = 100000000000.0;

  private static final double MAIN_SATELLITE_RADIUS = 600.0;
  private static final double MAIN_SATELLITE_MASS = 100.0;
  private static final double MAIN_SATELLITE_DISTANCE = 2.3 * EARTH_RADIUS;

  private static final double SECONDARY_SATELLITE_RADIUS = 400.0;
  private static final double SECONDARY_SATELLITE_MASS = 70.0;
  private static final double SECONDARY_SATELLITE_DISTANCE = 2.1 * EARTH_RADIUS;

  private static final double TETHER_PIECE_MASS = 10;
  private static final int TETHER_PIECE_COUNT = 20;

  private static final String BACKGROUND_IMAGE_FILE = "images/space_background.jpg";
  private static final String EARTH_IMAGE_FILE = "images/earth.png";
  private static final String MAIN_SATELLITE_IMAGE_FILE = "images/satellite_main.png";
  private static final String SECONDARY_SATELLITE_IMAGE_FILE = "images/satellite_secondary.png";

  // COLLISION_ELASTICITY should be between 0 and a little less than 1 for realistic physics.
  // 0 means collisions are completely inelastic; 1 means completely elastic.
  // But stay a little bit under 1, because of energy leakage.
  private static final double COLLISION_ELASTICITY = 0.95;

  private static final double TETHER_REBOUND_ELASTICITY = 1.0;

  private static final double COEFFICIENT_OF_FRICTION = 0.1;

  private static final double FPS_DESIRED = 60.0;

  private JComponent simCanvas;
  private JRadioButton tetherHoldButton;

  private List<PhysicsObject> physicsObjects = new ArrayList<>();

  private GravitySource earthGravity;

  private Object physicsLock = new Object();

  private enum TetherState {
    RETRACTING,
    HOLDING,
    EXTENDING,
  }

  private TetherState tetherState = TetherState.HOLDING;

  public static void main(String[] args) {
    new TetherSim().start();
  }

  public TetherSim() {
    createSimCanvas();
    JComponent tetherControl = createTetherControl();

    JFrame frame = new JFrame("TetherSim");

    frame.add(simCanvas);
    frame.add(tetherControl, BorderLayout.EAST);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }

  private void createSimCanvas() {
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

    physicsObjects.add(earth);

    physicsObjects.addAll(
        createOrbitingTetheredSatellite(
            earth,
            MAIN_SATELLITE_DISTANCE,
            MAIN_SATELLITE_MASS,
            MAIN_SATELLITE_RADIUS,
            mainSatelliteImage,
            SECONDARY_SATELLITE_DISTANCE,
            SECONDARY_SATELLITE_MASS,
            SECONDARY_SATELLITE_RADIUS,
            secondarySatelliteImage,
            TETHER_PIECE_COUNT));

    this.earthGravity = new GravitySource(earth);

    this.simCanvas = new SimCanvas(SPACE_VIEW_WIDTH, backgroundImage, physicsObjects, physicsLock);
    simCanvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
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

  private List<PhysicsObject> createOrbitingTetheredSatellite(
      PhysicsObject earth,
      double distanceA,
      double massA,
      double radiusA,
      BufferedImage imageA,
      double distanceB,
      double massB,
      double radiusB,
      BufferedImage imageB,
      int tetherPieceCount) {
    PhysicsObject objB = createSatellite(distanceB, massB, radiusB, imageB, null);
    List<PhysicsObject> tether = createTether(objB, distanceA - radiusA, tetherPieceCount);
    PhysicsObject objA =
        createSatellite(distanceA, massA, radiusA, imageA, tether.get(tether.size() - 1));

    List<PhysicsObject> satellite = new ArrayList<>(tether);
    satellite.add(objA);
    satellite.add(objB);

    pushTetheredSatelliteIntoCircularOrbit(satellite, earth);

    return satellite;
  }

  private PhysicsObject createSatellite(
      double distance,
      double mass,
      double radius,
      BufferedImage image,
      PhysicsObject downlinkObject) {
    PhysicsObjectBuilder builder =
        new PhysicsObjectBuilder()
            .position(new Vec2D(0.0, distance))
            .mass(mass)
            .radius(radius)
            .momentOfInertia(momentOfInertiaForDisc(mass, radius))
            .image(image)
            .hookUplink(new Vec2D(0.0, radius))
            .hookDownlink(new Vec2D(0.0, -radius));

    if (downlinkObject != null) {
      builder.downlinkTo(downlinkObject);
    }

    return builder.build();
  }

  private List<PhysicsObject> createTether(
      PhysicsObject bottomObject, double topHookDistance, int pieceCount) {
    double bottomHookDistance = bottomObject.hookUplinkWorldCoords().length();

    List<PhysicsObject> tether = new ArrayList<>();

    for (int i = 0; i < pieceCount; i++) {
      double distance =
          bottomHookDistance
              + (i + 1) * ((topHookDistance - bottomHookDistance) / (pieceCount + 1));

      PhysicsObject piece =
          new PhysicsObjectBuilder()
              .position(new Vec2D(0.0, distance))
              .downlinkTo(tether.size() == 0 ? bottomObject : tether.get(tether.size() - 1))
              .mass(TETHER_PIECE_MASS)
              .build();
      tether.add(piece);
    }

    return tether;
  }

  private void pushTetheredSatelliteIntoCircularOrbit(
      List<PhysicsObject> satellite, PhysicsObject earth) {
    Vec2D positionWeightedSum =
        earth
            .position()
            .scale(earth.mass())
            .add(
                satellite.stream()
                    .map(po -> po.position().scale(po.mass()))
                    .reduce(new Vec2D(), (a, b) -> a.add(b)));
    double massTotal = earth.mass() + satellite.stream().mapToDouble(po -> po.mass()).sum();
    Vec2D barycenter = positionWeightedSum.scale(1.0 / massTotal);
    double b = earth.position().distanceTo(barycenter);

    double massOverDistanceSquaredSum =
        satellite.stream()
            .mapToDouble(po -> po.mass() / (po.position().distanceSquaredTo(earth.position())))
            .sum();

    double angularSpeed = Math.sqrt(G * massOverDistanceSquaredSum / b);

    for (PhysicsObject po : satellite) {
      Vec2D velocity = po.position().sub(barycenter).rotate(Math.PI / 2.0).scale(angularSpeed);
      Vec2D impulse = velocity.scale(po.mass());
      po.feelImpulse(impulse);
      earth.feelImpulse(impulse.flip());

      po.feelAngularImpulse(angularSpeed * po.momentOfInertia());
    }
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

  private JComponent createTetherControl() {
    JRadioButton retractButton =
        new JRadioButton(
            new AbstractAction("Retract") {
              public void actionPerformed(ActionEvent e) {
                tetherState = TetherState.RETRACTING;
              }
            });
    this.tetherHoldButton =
        new JRadioButton(
            new AbstractAction("Hold") {
              public void actionPerformed(ActionEvent e) {
                tetherState = TetherState.HOLDING;
              }
            });
    JRadioButton extendButton =
        new JRadioButton(
            new AbstractAction("Extend") {
              public void actionPerformed(ActionEvent e) {
                tetherState = TetherState.EXTENDING;
              }
            });

    ButtonGroup group = new ButtonGroup();
    group.add(retractButton);
    group.add(tetherHoldButton);
    group.add(extendButton);

    tetherHoldButton.setSelected(true);

    final int titleMargin = 20;
    final int buttonMargin = 10;

    Box box = Box.createVerticalBox();

    box.add(Box.createVerticalGlue());

    box.add(new JLabel("Tether Control"));
    box.add(Box.createVerticalStrut(titleMargin));
    box.add(retractButton);
    box.add(Box.createVerticalStrut(buttonMargin));
    box.add(tetherHoldButton);
    box.add(Box.createVerticalStrut(buttonMargin));
    box.add(extendButton);

    box.add(Box.createVerticalGlue());

    return box;
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

  private void checkNaN(String operation) {
    for (PhysicsObject po : physicsObjects) {
      checkNaN(po.velocity(), operation);
    }
  }

  private void checkNaN(Vec2D vec, String operation) {
    checkNaN(vec.x(), operation);
    checkNaN(vec.y(), operation);
  }

  private void checkNaN(double val, String operation) {
    if (Double.isNaN(val)) {
      System.out.println("NaN after " + operation);
      System.exit(1);
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
    if (velRelativeLen == 0.0) {
      // The two surfaces are not sliding along each other, so there
      // is no friction to apply.  In fact, we'd get NaN everywhere
      // if we were to try, because the very next thing we do is divide
      // by velRelativeLen.
      return;
    }

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
