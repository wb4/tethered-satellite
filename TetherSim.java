import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

public class TetherSim {

  private static final int VIEW_WIDTH = 2000;
  private static final int VIEW_HEIGHT = 2000;

  private static final double SPACE_VIEW_WIDTH = 40000.0;

  private static final double EARTH_RADIUS = 12742.0 / 2.0;
  private static final double EARTH_MASS = 100000000000.0;

  private static final double SATELLITE_RADIUS = 600.0;
  private static final double SATELLITE_MASS = 100.0;

  private static final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");
  private static final File EARTH_IMAGE_FILE = new File("earth.png");
  private static final File MAIN_SATELLITE_IMAGE_FILE = new File("satellite_main.png");

  private static final double FPS_DESIRED = 60.0;

  private JComponent simCanvas;
  private PhysicsObject satellite;

  private Object physicsLock = new Object();

  public static void main(String[] args) {
    new TetherSim().start();
  }

  public TetherSim() {
    JFrame frame = new JFrame("TetherSim");

    BufferedImage backgroundImage = loadImageOrDie(BACKGROUND_IMAGE_FILE);
    BufferedImage earthImage = loadImageOrDie(EARTH_IMAGE_FILE);
    BufferedImage mainSatelliteImage = loadImageOrDie(MAIN_SATELLITE_IMAGE_FILE);

    this.satellite =
        new PhysicsObject(
            new Vec2D(0.0, 2.0 * EARTH_RADIUS),
            new Vec2D(-2800, 0),
            SATELLITE_MASS,
            SATELLITE_RADIUS,
            mainSatelliteImage);

    this.simCanvas =
        new SimCanvas(
            backgroundImage, earthImage, SPACE_VIEW_WIDTH, EARTH_RADIUS, satellite, physicsLock);
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

  public void start() {
    new Thread(() -> run(), "Physics Simulator").start();
  }

  private void run() {
    final long targetMillisPerFrame = (long) (1000.0 / FPS_DESIRED);

    long lastTickTimeMillis = System.currentTimeMillis();

    for (; ; ) {
      long startTimeMillis = System.currentTimeMillis();

      double tickSecs = (startTimeMillis - lastTickTimeMillis) / 1000.0;

      synchronized (physicsLock) {
        tickPhysics(tickSecs);
      }

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
    satellite.feelGravity(new Vec2D(0, 0), EARTH_MASS, secs);
    satellite.move(secs);
  }
}

class SimCanvas extends JComponent {

  private BufferedImage backgroundImage;
  private BufferedImage earthImage;

  private double spaceViewWidth;
  private double earthRadius;

  private PhysicsObject satellite;

  private Object physicsLock;

  public SimCanvas(
      BufferedImage backgroundImage,
      BufferedImage earthImage,
      double spaceViewWidth,
      double earthRadius,
      PhysicsObject satellite,
      Object physicsLock) {
    this.backgroundImage = backgroundImage;
    this.earthImage = earthImage;

    this.spaceViewWidth = spaceViewWidth;
    this.earthRadius = earthRadius;

    this.satellite = satellite;

    this.physicsLock = physicsLock;
  }

  protected void paintComponent(Graphics legacyG) {
    Graphics2D g = (Graphics2D) legacyG;

    drawBackground(g);
    drawEarth(g);

    synchronized (physicsLock) {
      drawSatellite(g);
    }
  }

  private void drawBackground(Graphics2D g) {
    int xOff = (getWidth() - backgroundImage.getWidth()) / 2;
    int yOff = (getHeight() - backgroundImage.getHeight()) / 2;

    g.drawImage(backgroundImage, null, xOff, yOff);
  }

  private void drawEarth(Graphics2D g) {
    drawImageInWorld(g, earthImage, new Vec2D(0, 0), earthRadius);
  }

  private void drawSatellite(Graphics2D g) {
    drawImageInWorld(g, satellite.image(), satellite.position(), satellite.radius());
  }

  private void drawImageInWorld(Graphics2D g, BufferedImage image, Vec2D position, double radius) {
    AffineTransform transform = new AffineTransform();

    transform.translate(getWidth() / 2, getHeight() / 2);

    double viewScale = Math.min(getWidth(), getHeight()) / spaceViewWidth;
    transform.scale(viewScale, viewScale);

    transform.translate(position.x(), -position.y());
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
  private double radius;
  private BufferedImage image;

  public PhysicsObject(
      Vec2D position, Vec2D velocity, double mass, double radius, BufferedImage image) {
    this.position = position;
    this.velocity = velocity;
    this.mass = mass;
    this.radius = radius;
    this.image = image;
  }

  public Vec2D position() {
    return position;
  }

  public double radius() {
    return radius;
  }

  public BufferedImage image() {
    return image;
  }

  public void move(double secs) {
    position = position.add(velocity.scale(secs));
  }

  public void feelGravity(Vec2D otherPosition, double otherMass, double secs) {
    Vec2D offset = otherPosition.sub(position);
    Vec2D normalizedDirection = offset.normalized();
    double forceMagnitude = mass * otherMass / offset.lengthSquared();

    feelForce(normalizedDirection.scale(forceMagnitude), secs);
  }

  public void feelForce(Vec2D force, double secs) {
    feelImpulse(force.scale(secs));
  }

  public void feelImpulse(Vec2D impulse) {
    velocity = velocity.add(impulse.scale(1.0 / mass));
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
