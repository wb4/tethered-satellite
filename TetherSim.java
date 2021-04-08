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

  private static final double WORLD_VIEW_WIDTH = 20000.0;
  private static final double EARTH_DIAMETER = 12742.0;

  private static final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");
  private static final File EARTH_IMAGE_FILE = new File("earth.png");

  public static void main(String[] args) {
    new TetherSim().run();
  }

  private void run() {
    JFrame frame = new JFrame("TetherSim");

    BufferedImage backgroundImage = loadImageOrDie(BACKGROUND_IMAGE_FILE);
    BufferedImage earthImage = loadImageOrDie(EARTH_IMAGE_FILE);

    JComponent canvas =
        new SimCanvas(backgroundImage, earthImage, WORLD_VIEW_WIDTH, EARTH_DIAMETER);
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

  private double worldViewWidth;
  private double earthDiameter;

  public SimCanvas(
      BufferedImage backgroundImage,
      BufferedImage earthImage,
      double worldViewWidth,
      double earthDiameter) {
    this.backgroundImage = backgroundImage;
    this.earthImage = earthImage;

    this.worldViewWidth = worldViewWidth;
    this.earthDiameter = earthDiameter;
  }

  protected void paintComponent(Graphics legacyG) {
    Graphics2D g = (Graphics2D) legacyG;

    drawBackground(g);
    drawEarth(g);
  }

  private void drawBackground(Graphics2D g) {
    int xOff = (getWidth() - backgroundImage.getWidth()) / 2;
    int yOff = (getHeight() - backgroundImage.getHeight()) / 2;

    g.drawImage(backgroundImage, null, xOff, yOff);
  }

  private void drawEarth(Graphics2D g) {
    AffineTransform transform = new AffineTransform();

    transform.translate(getWidth() / 2, getHeight() / 2);

    double viewScale = Math.min(getWidth(), getHeight()) / worldViewWidth;
    transform.scale(viewScale, viewScale);

    transform.scale(earthDiameter / earthImage.getWidth(), earthDiameter / earthImage.getHeight());
    transform.translate(-earthImage.getWidth() / 2, -earthImage.getHeight() / 2);

    BufferedImageOp imageOp =
        new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

    g.drawImage(earthImage, imageOp, 0, 0);
  }
}
