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
  private static final double SATELLITE_RADIUS = 600.0;

  private static final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");
  private static final File EARTH_IMAGE_FILE = new File("earth.png");
  private static final File MAIN_SATELLITE_IMAGE_FILE = new File("satellite_main.png");

  public static void main(String[] args) {
    new TetherSim().run();
  }

  private void run() {
    JFrame frame = new JFrame("TetherSim");

    BufferedImage backgroundImage = loadImageOrDie(BACKGROUND_IMAGE_FILE);
    BufferedImage earthImage = loadImageOrDie(EARTH_IMAGE_FILE);
    BufferedImage mainSatelliteImage = loadImageOrDie(MAIN_SATELLITE_IMAGE_FILE);

    PhysicsObject satellite =
        new PhysicsObject(new Vec2D(0.0, 2.0 * EARTH_RADIUS), SATELLITE_RADIUS, mainSatelliteImage);

    JComponent canvas =
        new SimCanvas(backgroundImage, earthImage, SPACE_VIEW_WIDTH, EARTH_RADIUS, satellite);
    canvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
    frame.add(canvas);

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
}

class SimCanvas extends JComponent {

  private BufferedImage backgroundImage;
  private BufferedImage earthImage;

  private double spaceViewWidth;
  private double earthRadius;

  private PhysicsObject satellite;

  public SimCanvas(
      BufferedImage backgroundImage,
      BufferedImage earthImage,
      double spaceViewWidth,
      double earthRadius,
      PhysicsObject satellite) {
    this.backgroundImage = backgroundImage;
    this.earthImage = earthImage;

    this.spaceViewWidth = spaceViewWidth;
    this.earthRadius = earthRadius;

    this.satellite = satellite;
  }

  protected void paintComponent(Graphics legacyG) {
    Graphics2D g = (Graphics2D) legacyG;

    drawBackground(g);
    drawEarth(g);
    drawSatellite(g);
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
  private double radius;
  private BufferedImage image;

  public PhysicsObject(Vec2D position, double radius, BufferedImage image) {
    this.position = position;
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
}
