import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class sipnnsim1 {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cart-Pole Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            SimPanel panel = new SimPanel();
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

class SimPanel extends JPanel {

    // --- gains, same values as your main() ---
    static final double K1 = -1.1623;
    static final double K2 = -5.0075;
    static final double K3 = 55.3019;
    static final double K4 = 9.1142;
    static final double DT = 0.001;

    environment env;
    control ctrl;
    Timer timer;
    long lastNanos;
    double simTime = 0;
    double lastForce = 0;
    boolean running = true;

    // rendering
    final int W = 900, H = 480;
    final int trackY = 260;
    double pxPerMeter = 150;

    JButton startPause, reset;
    JLabel status;

    SimPanel() {
        setPreferredSize(new Dimension(W, H + 40));
        setBackground(new Color(18, 18, 22));

        env = new environment("");
        ctrl = new control(env.M, env.m, env.L, K1, K2, K3, K4);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setOpaque(false);
        startPause = new JButton("Pause");
        reset = new JButton("Reset");
        status = new JLabel();
        status.setForeground(Color.WHITE);
        controls.add(startPause);
        controls.add(reset);
        controls.add(status);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.SOUTH);

        startPause.addActionListener(e -> {
            running = !running;
            startPause.setText(running ? "Pause" : "Start");
        });
        reset.addActionListener(e -> {
            env = new environment("");
            env.reset(0.2f);
            ctrl = new control(env.M, env.m, env.L, K1, K2, K3, K4);
            simTime = 0;
        });

        lastNanos = System.nanoTime();
        timer = new Timer(16, e -> tick()); // ~60 fps
        timer.start();
    }

    void tick() {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1e9;
        lastNanos = now;
        if (running) {
            elapsed = Math.min(elapsed, 0.05); // avoid huge catch-up jumps
            int steps = (int) (elapsed / DT);
            for (int i = 0; i < steps; i++) {
                lastForce = ctrl.fuckyou(env.x, env.xd, env.theta, env.thetad);
                env.rk4(lastForce, DT);
                //if((int)(simTime*1000)%50 == 0) env.print(simTime, lastForce);
                simTime += DT;
            }
        }
        status.setText(String.format(
            "t=%.2fs  x=%.3f  theta=%.3f rad  force=%.2f  mode=%s",
            simTime, env.x, env.theta, lastForce, ctrl.method ? "swing-up" : "LQR"));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int centerX = W / 2;

        // track
        g.setColor(new Color(70, 70, 80));
        g.drawLine(0, trackY, W, trackY);

        // track limits (x_max not set on this constructor -> just show +-2m guide)
        double limit = 2.0;
        int leftPx = (int) (centerX - limit * pxPerMeter);
        int rightPx = (int) (centerX + limit * pxPerMeter);
        g.setColor(new Color(50, 50, 58));
        g.drawLine(leftPx, trackY - 10, leftPx, trackY + 10);
        g.drawLine(rightPx, trackY - 10, rightPx, trackY + 10);

        // cart
        int cartW = 70, cartH = 36;
        int cartX = (int) (centerX + env.x * pxPerMeter);
        int cartY = trackY;
        g.setColor(new Color(90, 170, 240));
        RoundRectangle2D cartShape = new RoundRectangle2D.Double(
            cartX - cartW / 2.0, cartY - cartH / 2.0, cartW, cartH, 8, 8);
        g.fill(cartShape);
        g.setColor(new Color(200, 220, 255));
        g.draw(cartShape);

        // wheels
        g.setColor(new Color(30, 30, 34));
        g.fillOval(cartX - cartW / 2 + 6, cartY + cartH / 2 - 6, 14, 14);
        g.fillOval(cartX + cartW / 2 - 20, cartY + cartH / 2 - 6, 14, 14);

        // pole: theta=0 hangs straight down, theta=PI is upright
        double L_px = env.L * pxPerMeter;
        int pivotX = cartX;
        int pivotY = cartY;
        int tipX = (int) (pivotX + L_px * Math.sin(env.theta));
        int tipY = (int) (pivotY + L_px * Math.cos(env.theta));

        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(240, 140, 90));
        g.drawLine(pivotX, pivotY, tipX, tipY);

        g.setColor(new Color(250, 220, 90));
        g.fillOval(tipX - 10, tipY - 10, 20, 20);

        g.setColor(new Color(230, 230, 235));
        g.fillOval(pivotX - 5, pivotY - 5, 10, 10);

        // upright reference (faint)
        g.setColor(new Color(60, 90, 60));
        g.drawLine(pivotX, pivotY, pivotX, pivotY - (int) L_px);

        // force arrow
        if (Math.abs(lastForce) > 0.01) {
            g.setColor(new Color(255, 90, 90));
            int dir = lastForce > 0 ? 1 : -1;
            int arrowLen = (int) Math.min(60, Math.abs(lastForce) * 2);
            int baseX = cartX + dir * (cartW / 2 + 4);
            g.setStroke(new BasicStroke(3));
            g.drawLine(baseX, cartY, baseX + dir * arrowLen, cartY);
            g.fillPolygon(
                new int[]{baseX + dir * arrowLen, baseX + dir * arrowLen - dir * 8, baseX + dir * arrowLen - dir * 8},
                new int[]{cartY, cartY - 5, cartY + 5}, 3);
        }
    }
}

// ---- unchanged simulation classes from your file ----

class environment {
    double M;
    double m;
    double L;
    double xdd;
    double xd;
    double x;
    double thetadd;
    double thetad;
    double theta;
    double g = 9.7905;
    double x_max;
    boolean noise_sw;
    double noise;

    public environment(double mass_cart, double mass_rod, double length_rod, double initial_cart_acceleration, double initial_cart_velocity, double initial_cart_position, double initial_rod_angular_acceleration, double initial_rod_angular_velocity, double initial_rod_theta, double max_cart_displacement, double noise_fc) {
        M = mass_cart; m = mass_rod; L = length_rod;
        xdd = initial_cart_acceleration; xd = initial_cart_velocity; x = initial_cart_position;
        thetadd = initial_rod_angular_acceleration; thetad = initial_rod_angular_velocity; theta = initial_rod_theta;
        x_max = max_cart_displacement; noise = noise_fc; noise_sw = true;
    }
    public environment(double mass_cart, double mass_rod, double length_rod, double initial_cart_acceleration, double initial_cart_velocity, double initial_cart_position, double initial_rod_angular_acceleration, double initial_rod_angular_velocity, double initial_rod_theta, double max_cart_displacement) {
        M = mass_cart; m = mass_rod; L = length_rod;
        xdd = initial_cart_acceleration; xd = initial_cart_velocity; x = initial_cart_position;
        thetadd = initial_rod_angular_acceleration; thetad = initial_rod_angular_velocity; theta = initial_rod_theta;
        x_max = max_cart_displacement; noise_sw = false;
    }
    public environment(String defaultconfig) {
        M = 1; m = 1; L = 1;
        xdd = 0; xd = -0.5; x = 0;
        thetadd = 0; thetad = 0.5; theta = Math.toRadians(0);
        x_max = 2; noise_sw = false;
    }

    private void calc(double x1, double xd1, double theta1, double thetad1, double force) {
        double a1 = m + M;
        double b1 = m * L * Math.cos(theta1) / 2;
        double c1 = -((m * L * thetad1 * thetad1 * Math.sin(theta1) / 2) + force);
        double a2 = m * L * Math.cos(theta1) / 2;
        double b2 = m * L * L / 3;
        double c2 = m * L * g * Math.sin(theta1) / 2;
        double denom = a1 * b2 - a2 * b1;
        xdd = (b1 * c2 - b2 * c1) / denom;
        thetadd = (c1 * a2 - c2 * a1) / denom;
    }
    void reset(float difficulty){
        x = 0;
        xd = (Math.random() * difficulty - difficulty/2);
        theta = (Math.random() * 2*difficulty - difficulty);
        thetad = (Math.random() * 2*difficulty - difficulty);
        xdd = 0;
        thetadd = 0;
    }
    void rk4(double force, double dt) {
        if(x > 3){
            force = 0;
            if(xd>0) xd = 0;
        }
        if(x < -3){
            force = 0;
            if(xd<0) xd = 0;
        }
        double x1 = x;
        double xd1 = xd;
        double theta1 = theta;
        double thetad1 = thetad;
        calc(x1, xd1, theta1, thetad1, force);
        double xdd1 = xdd;
        double thetadd1 = thetadd;

        double x2 = x + xd1 * (dt / 2);
        double xd2 = xd + xdd1 * (dt / 2);
        double theta2 = theta + thetad1 * (dt / 2);
        double thetad2 = thetad + thetadd1 * (dt / 2);
        calc(x2, xd2, theta2, thetad2, force);
        double xdd2 = xdd;
        double thetadd2 = thetadd;

        double x3 = x + xd2 * (dt / 2);
        double xd3 = xd + xdd2 * (dt / 2);
        double theta3 = theta + thetad2 * (dt / 2);
        double thetad3 = thetad + thetadd2 * (dt / 2);
        calc(x3, xd3, theta3, thetad3, force);
        double xdd3 = xdd;
        double thetadd3 = thetadd;

        double x4 = x + xd3 * (dt);
        double xd4 = xd + xdd3 * (dt);
        double theta4 = theta + thetad3 * (dt);
        double thetad4 = thetad + thetadd3 * (dt);
        calc(x4, xd4, theta4, thetad4, force);
        double xdd4 = xdd;
        double thetadd4 = thetadd;

        x += (dt / 6.0) * (xd1 + 2 * xd2 + 2 * xd3 + xd4);
        xd += (dt / 6.0) * (xdd1 + 2 * xdd2 + 2 * xdd3 + xdd4);
        theta += (dt / 6.0) * (thetad1 + 2 * thetad2 + 2 * thetad3 + thetad4);
        thetad += (dt / 6.0) * (thetadd1 + 2 * thetadd2 + 2 * thetadd3 + thetadd4);
    }
    void print(double dt, double force){
        System.out.println("time: " + dt + " | force: " + force + " | xdd: " + xdd + " | xd: " + xd + " | x: " + x + " | thetadd: " + thetadd + " | thetad: " + thetad + " | theta: " + theta);
    }
}
class control {
    double M;
    double m;
    double L;
    double g = 9.7905;
    double K1, K2, K3, K4;
    double threshold = 0.4;
    double kEnergy = 45.0; 
    double maxForce = 30;
    boolean method = false;
    double Max_length = 2;

    double brkdistance = 0.45;
    double brkc = 20;
    double accc = 15;
    boolean initial = true;
    double target;
    double idkwhat = 0.15;
    double vdanger = 9.5;
    double swingdistance = 0.75;
    int wait = 100; //millisec
    int current = 0;
    int dhrcyclecount = 1;
    double xinitial;
    boolean initial_note_boolean = true;
 
    boolean drive = true;
    boolean hook = false;
    boolean relief = false;


    public control(double Mass, double mass, double Length, double k1, double k2, double k3, double k4) {
        this.M = Mass;
        this.m = mass;
        this.L = Length;
        this.K1 = k1;
        this.K2 = k2;
        this.K3 = k3;
        this.K4 = k4;
    }

    double catchThreshold = Math.toRadians(45);

    public double fuckyou(double x, double xd, double theta, double thetad) {
        method = true;
        double error = theta - Math.PI;
        error = Math.atan2(Math.sin(error), Math.cos(error));

        double lqrForce = -(K1 * x + K2 * xd + K3 * error + K4 * thetad);
        if (Math.abs(error) < threshold) {
            return Math.max(-maxForce, Math.min(maxForce, lqrForce));
        }
        if (Math.abs(error) < catchThreshold) {
            double catchForce = -(K3 * error + K4 * thetad);
            return Math.max(-maxForce, Math.min(maxForce, catchForce));
        }
        method = false;
        return claude_i_hate_you_soo_much_i_made_my_own_controller(x, xd, theta, thetad, error); 
    }
    private double claude_i_hate_you_soo_much_i_made_my_own_controller(double x, double xd, double theta, double thetad, double err) {
        if (initial) {
            
if (theta * thetad > 0) { // Standard energy pumping: drive in direction of angular motion
    if (thetad > 0) {
        target = -swingdistance * Max_length;
    } else {
        target = swingdistance * Max_length;
    }
    
    // Reset hold flag so next transition captures fresh position
    initial_note_boolean = true; 
    
    initial = false;
    hook = false;
    drive = true;
    relief = false;
} else {
    // Capture position once upon entering hold state
    if (initial_note_boolean) {
        xinitial = x;
        initial_note_boolean = false;
    }
    
    target = xinitial;
    hold_lqr(target, x, xd);
}
        }
        if (drive) {
            if (Math.abs(target - x) < idkwhat) {
                drive = false;
                hook = true;
                relief = false;
            }
            return driveforce(target, x, xd);
        }
        if (hook) {
            double sign = Math.signum(x);
            if (sign * thetad < 0) {
                hook = false;
                drive = false;
                relief = true;
                current = 0;
            }
            return hold_lqr(target, x, xd);
        }
        if (relief) {
            double sign = Math.signum(x);
            if(current == wait){
                hook = false;
                drive = true;
                relief = false;
                dhrcyclecount ++;
                target = -(sign) * Max_length * swingdistance;
            }
            current++;
            return hold_lqr(target, x, xd);
        }
        return 0.0; 
    }
    private double hold_lqr(double target, double x, double xd) {
        double KK1 = 80.06;
        double KK2 = 81.06; 
        return Math.max(-maxForce, Math.min(maxForce, -((x - target) * KK1 + xd * KK2)));
    }

    private double driveforce(double target, double x, double xd) {
        double xerr = target - x;

        if (Math.abs(xerr) < brkdistance){
            return Math.max(-maxForce, Math.min(maxForce, -xd * brkc));
        } 
        if(Math.abs(xerr) > brkdistance && Math.abs(xd) < vdanger ){
            return Math.max(-maxForce, Math.min(maxForce, accc * (target - x)));
        }
        return 0.0;
    }
    
}