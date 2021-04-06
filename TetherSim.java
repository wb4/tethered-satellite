
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;

public class TetherSim {

    static private final int VIEW_WIDTH = 2000;
    static private final int VIEW_HEIGHT = 2000;

    public static void main(String[] args) {
        new TetherSim().run();
    }

    private void run() {
        JFrame frame = new JFrame("TetherSim");

        JComponent canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        frame.add(canvas);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}

class SimCanvas extends JComponent {
}
