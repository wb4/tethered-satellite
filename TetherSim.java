
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import java.awt.image.BufferedImage;;

import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JFrame;

public class TetherSim {

    static private final int VIEW_WIDTH = 2000;
    static private final int VIEW_HEIGHT = 2000;

    static private final File BACKGROUND_IMAGE_FILE = new File("space_background.jpg");

    public static void main(String[] args) {
        new TetherSim().run();
    }

    private void run() {
        JFrame frame = new JFrame("TetherSim");

        BufferedImage backgroundImage = null;
        try {
            backgroundImage = ImageIO.read(BACKGROUND_IMAGE_FILE);
        } catch (IOException exc) {
            System.err.println("error loading image " + BACKGROUND_IMAGE_FILE + ": " + exc.getMessage());
            System.exit(1);
        }

        JComponent canvas = new SimCanvas(backgroundImage);
        canvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        frame.add(canvas);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}

class SimCanvas extends JComponent {

    private BufferedImage backgroundImage;

    public SimCanvas(BufferedImage backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    protected void paintComponent(Graphics legacyG) {
        Graphics2D g = (Graphics2D) legacyG;

        g.drawImage(backgroundImage, null, 0, 0);
    }

}
