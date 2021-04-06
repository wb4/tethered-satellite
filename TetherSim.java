
import javax.swing.JFrame;

public class TetherSim {

    public static void main(String[] args) {
        new TetherSim().run();
    }

    private void run() {
        JFrame frame = new JFrame("TetherSim");


        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}
