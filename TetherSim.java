import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

public class TetherSim {

  private static final int VIEW_WIDTH = 2000;
  private static final int VIEW_HEIGHT = 2000;

  private static final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");
  private static final File EARTH_IMAGE_FILE = new File("earth.png");

  public static void main(String[] args) {
    new TetherSim().run();
  }

  private void run() {
    JFrame frame = new JFrame("TetherSim");

    BufferedImage backgroundImage = loadImageOrDie(BACKGROUND_IMAGE_FILE);
    BufferedImage earthImage = loadImageOrDie(EARTH_IMAGE_FILE);

    JComponent canvas = new SimCanvas(backgroundImage);
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

  public SimCanvas(BufferedImage backgroundImage) {
    this.backgroundImage = backgroundImage;
  }

  protected void paintComponent(Graphics legacyG) {
    Graphics2D g = (Graphics2D) legacyG;

    drawBackground(g);
  }

  private void drawBackground(Graphics2D g) {
    int xOff = (getWidth() - backgroundImage.getWidth()) / 2;
    int yOff = (getHeight() - backgroundImage.getHeight()) / 2;

    g.drawImage(backgroundImage, null, xOff, yOff);
  }
}
