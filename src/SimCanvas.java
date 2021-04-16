import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.List;
import javax.swing.JComponent;

class SimCanvas extends JComponent {

  private static Color TETHER_COLOR = new Color(0.5f, 0.5f, 1.0f);

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
    drawImages(g);
    drawTethers(g);
  }

  private void drawImages(Graphics2D g) {
    for (PhysicsObject po : physicsObjects) {
      drawImageInWorld(g, po.image(), po.position(), po.angleRad(), po.radius());
    }
  }

  private void drawTethers(Graphics2D g) {
    for (PhysicsObject po : physicsObjects) {
      if (po.downlinkObject() != null) {
        drawLineInWorld(
            g,
            po.hookDownlinkWorldCoords(),
            po.downlinkObject().hookUplinkWorldCoords(),
            TETHER_COLOR);
      }
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

  private void drawLineInWorld(Graphics2D g, Vec2D p1, Vec2D p2, Color color) {
    AffineTransform transform = new AffineTransform();

    transform.translate(getWidth() / 2, getHeight() / 2);

    double viewScale = Math.min(getWidth(), getHeight()) / spaceViewWidth;
    transform.scale(viewScale, -viewScale);

    Shape line =
        transform.createTransformedShape(new Line2D.Double(p1.x(), p1.y(), p2.x(), p2.y()));
    g.setColor(color);
    g.draw(line);
  }
}
