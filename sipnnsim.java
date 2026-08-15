import java.util.Scanner;
import java.io.*;
import java.util.PriorityQueue;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.Random;
public class sipnnsim{
    public static void main(String[] args) throws Exception {
        double K1 = -1.1623; //hell 
        double K2 = -5.0075; //hell 
        double K3 = 55.3019; //hell 
        double K4 = 9.1142;  //hell 
        double dt = 0.001;
        String ANSI_RED = "\u001B[31m";
        String ANSI_RESET = "\u001B[0m";
        String ANSI_GREEN = "\033[32m";
        int time = 30;
        double learning_rate = 0.0025;
        int t = (int)(time/dt);
        boolean pass = false;
        int data =1_000_000;
        double[][] training_data = new double[data][5];
        double maxForce = 30;

        environment env = new environment("");env.reset((float)0.5);
        control ctrl = new control(env.M, env.m, env.L, K1, K2, K3 ,K4);
        Neuron nr = new Neuron();
        //nr.randgen();
        nr.setup();
        float difficulty = 0.75f; // Maximum difficulty
        env.reset(0.2f);
        for(int j = 0 ; j < 30000 ;j++){
            //double force = ctrl.fuckyou(env.x , env.xd, env.theta, env.thetad);
            double force = nr.neuron_calc(new double[]{env.x , env.xd, Math.sin(env.theta), Math.cos(env.theta), env.thetad});
            force = (force - 0.5)*60;

            env.rk4(force , dt);
            if(j % 50 == 0){
                env.printnn(j, force);
            }
        }

        double triggerslope = 0.0001;
        double cf_initial = 1;
        double cf_final = 1;
        int batch_size = 100;
        double slope;
        double mse = 0;
        int slackoff = 1;
        double control = 0;
        double output;
        int sessiontime = 20_000;
        Random rand = new Random();

        //LQR CLONE still cant work on full swing up


        for(int iiii = 0 ; iiii < 20000; iiii ++){
        System.out.print("loading training data >  ");
        for(int i = 0 ; i < data/sessiontime ; i++){
            for(int j = 0 ; j < sessiontime ;j++){
                double force = ctrl.fuckyou(env.x , env.xd, env.theta, env.thetad);
                training_data[i*20000 + j] = new double[]{env.x , env.xd, env.theta, env.thetad, force};
                env.rk4(force , dt);
            }
            env.resetpi(0.3f);
            System.out.print("|");
        }
        System.out.println("");
        
        
        for (int i = training_data.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            double[] temp = training_data[i];
            training_data[i] = training_data[j];
            training_data[j] = temp;
        }
        int training_episode= data / batch_size;
        for(int i = 0 ; i < training_episode ; i++){

            int add = i * batch_size;
            mse = 0;
            cf_initial = cf_final;

            for(int j = 0 ; j < batch_size ; j++){
                output = nr.neuron_calc(new double[]{(training_data[add + j][0]) , (training_data[add + j][1]), Math.sin(training_data[add + j][2]), Math.cos(training_data[add + j][2]), (training_data[add + j][3])});
                control = training_data[add + j][4]/(2*maxForce) + 0.5;
                nr.fuckingbackpropogayson((control), learning_rate);
                mse = mse + (control - output)*(control - output);
            }
            mse = mse/batch_size;
            cf_final = mse;
            slope = (cf_final - cf_initial)/batch_size;
            if(i%1000 == 0) System.out.println("step : " + i + " | Mse: " + mse + " | learning slope: " + slope + " | learning rate: " + learning_rate);   

            }
            if(iiii % 10 == 0){
                nr.print();
                env.resetpi(0.2f);
                for(int j = 0 ; j < 30000 ;j++){
                    //double force = ctrl.fuckyou(env.x , env.xd, env.theta, env.thetad);
                    double force = nr.neuron_calc(new double[]{env.x , env.xd, Math.sin(env.theta), Math.cos(env.theta), env.thetad});
                    force = (force - 0.5)*60;

                    env.rk4(force , dt);
                    if(j % 200 == 0){
                        env.printnn(j, force);
                    }
                }
            }
        }
    }
}
class environment{
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
    String ANSI_RED = "\u001B[31m";
    String ANSI_RESET = "\u001B[0m";
    String ANSI_GREEN = "\033[32m";
    public environment(double mass_cart, double mass_rod, double length_rod, double initial_cart_acceleration, double initial_cart_velocity, double initial_cart_position, double initial_rod_angular_acceleration, double initial_rod_angular_velocity, double initial_rod_theta, double max_cart_displacement, double noise_fc){
        M = mass_cart; m = mass_rod; L = length_rod;
        xdd = initial_cart_acceleration; xd = initial_cart_velocity; x = initial_cart_position;
        thetadd = initial_rod_angular_acceleration; thetad = initial_rod_angular_velocity; theta = initial_rod_theta;
        x_max = max_cart_displacement; noise = noise_fc; noise_sw = true;
    }
    public environment(double mass_cart, double mass_rod, double length_rod, double initial_cart_acceleration, double initial_cart_velocity, double initial_cart_position, double initial_rod_angular_acceleration, double initial_rod_angular_velocity, double initial_rod_theta, double max_cart_displacement){
        M = mass_cart; m = mass_rod; L = length_rod;
        xdd = initial_cart_acceleration; xd = initial_cart_velocity; x = initial_cart_position;
        thetadd = initial_rod_angular_acceleration; thetad = initial_rod_angular_velocity; theta = initial_rod_theta;
        x_max = max_cart_displacement; noise_sw = false;
    }
    public environment(String defaultconfig){
        M = 1; m = 1; L = 1;
        xdd = 0; xd = 0.1; x = 0;
        thetadd = 0; thetad = 0.1; theta = Math.toRadians(0);
        x_max = 2; noise_sw = false;
    }

    void print(double dt, double force, boolean method){
        System.out.print("time(ms): " + dt + " | force: " + force + " | xdd: " + xdd + " | xd: " + xd + " | x: " + x + " | thetadd: " + thetadd + " | thetad: " + thetad + " | theta: " + theta);
        if(method){
            System.out.println(" Method: lqr");
        }else{
            System.out.println(" Method: Swingup");
        }
    }
    void printnn(double dt, double force){
        System.out.println("time(ms): " + dt + " | force: " + force + " | xdd: " + xdd + " | xd: " + xd + " | x: " + x + " | thetadd: " + thetadd + " | thetad: " + thetad + " | theta: " + theta);
    }
    private void calc(double x1, double xd1, double theta1, double thetad1, double force){
        double a1 = m + M;
        double b1 = m*L*Math.cos(theta1)/2;
        double c1 = -((m*L*thetad1*thetad1*Math.sin(theta1)/2) + force);
        double a2 = m*L*Math.cos(theta1)/2;
        double b2 = m*L*L/3;
        double c2 = m*L*g*Math.sin(theta1)/2;
        double denom = a1*b2 - a2*b1;
        xdd = (b1*c2 - b2*c1)/denom;
        thetadd = (c1*a2 - c2*a1)/denom;
    }

    void reset(float difficulty){
        x = (Math.random() * 2*difficulty - difficulty);
        xd = (Math.random() * difficulty - difficulty/2);
        theta = (Math.random() * 2*difficulty - difficulty);
        thetad = (Math.random() * 2*difficulty - difficulty);
        xdd = 0;
        thetadd = 0;
    }
    void resetpi(float difficulty){ // difficuily shuldnt be over 0.45 itll goo ut of lqr catch range :3
        x = (Math.random() * 2*2.5 - 2.5);
        xd = (Math.random() * difficulty - difficulty/2);
        theta = (Math.random() * 2*difficulty - difficulty) + 3.14;
        thetad = (Math.random() * 2*difficulty - difficulty);
        xdd = 0;
        thetadd = 0;
    }

    void rk4(double force, double dt){
        if(x > 3){
            force = 0;
            if(xd>0) xd = 0;
        }
        if(x < -3){
            force = 0;
            if(xd<0) xd = 0;
        }
        double       x1 = x;
        double      xd1 = xd;
        double   theta1 = theta;
        double  thetad1 = thetad;
        calc(x1, xd1, theta1, thetad1, force);
        double     xdd1 = xdd;
        double thetadd1 = thetadd;

        double       x2 = x +           xd1*(dt/2);
        double      xd2 = xd +         xdd1*(dt/2);
        double   theta2 = theta +   thetad1*(dt/2);
        double  thetad2 = thetad + thetadd1*(dt/2);
        calc(x2, xd2, theta2, thetad2, force);
        double     xdd2 = xdd;
        double thetadd2 = thetadd;

        double       x3 = x +           xd2*(dt/2);
        double      xd3 = xd +         xdd2*(dt/2);
        double   theta3 = theta +   thetad2*(dt/2);
        double  thetad3 = thetad + thetadd2*(dt/2);
        calc(x3, xd3, theta3, thetad3, force);
        double     xdd3 = xdd;
        double thetadd3 = thetadd;

        double       x4 = x +           xd3*(dt);
        double      xd4 = xd +         xdd3*(dt);
        double   theta4 = theta +   thetad3*(dt);
        double  thetad4 = thetad + thetadd3*(dt);
        calc(x4, xd4, theta4, thetad4, force);
        double     xdd4 = xdd;
        double thetadd4 = thetadd;

        x      += (dt/6.0)*(xd1 + 2*xd2 + 2*xd3 + xd4);
        xd     += (dt/6.0)*(xdd1 + 2*xdd2 + 2*xdd3 + xdd4);
        theta  += (dt/6.0)*(thetad1 + 2*thetad2 + 2*thetad3 + thetad4);
        thetad += (dt/6.0)*(thetadd1 + 2*thetadd2 + 2*thetadd3 + thetadd4);
    }
}
class control{
    double M;
    double m;
    double L;
    double g=9.7905;
    double K1,K2,K3,K4;
    double threshold = 0.4;
    double catchThreshold = Math.toRadians(45);
    Neuron nr_swingup = new Neuron();
    double maxForce = 30;
    boolean method = false;

    double brkdistance = 0.45;
    double brkc = 20;
    double accc = 12.2;
    boolean initial = true;
    double target;
    double idkwhat = 0.15;
    double vdanger = 9.5;
    double swingdistance = 0.75;
    int wait = 100; //millisec, this gud shi
    int current = 0;
    double dhrcyclecount = 0;
    double xinitial;
    boolean initial_note_boolean = true;

    boolean drive = true;
    boolean hook = false;
    boolean relief = false;

    double Max_length = 2; //aka +-2 this is just to trick the controller it always misbehaves its actually +-3
    Neuron_fucker nr_lqr = new Neuron_fucker();
    public control(double Mass, double mass, double Length, double k1, double k2, double k3, double k4)throws Exception{
        this.M = Mass;
        this.m = mass;
        this.L = Length;
        this.K1 = k1;
        this.K2 = k2;
        this.K3 = k3;
        this.K4 = k4;
        nr_swingup.setup();
    }
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
            
            if (theta * thetad > 0) {
                if (thetad > 0) {
                    target = -swingdistance * Max_length;
                } else {
                    target = swingdistance * Max_length;
                }
                initial_note_boolean = true; 
                initial = false;
                hook = false;
                drive = true;
                relief = false;
            } else {
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
    double neuralnetwork(double x, double xd, double theta, double thetad) throws Exception{
        method = true;
        double error = theta - Math.PI;
        error = Math.atan2(Math.sin(error ), Math.cos(error));
        double lqrForce = nr_lqr.neuron_calc(new double[]{x,  xd,  theta,  thetad});
        if(Math.abs(error) < threshold) return Math.max(-maxForce, Math.min(maxForce, lqrForce));
        method = false;
        double swingupforce = nr_swingup.neuron_calc(new double[]{(x) , (xd), Math.sin(theta), Math.cos(theta), (thetad)});
        swingupforce = maxForce*2*(swingupforce - 0.5);
        return Math.max(-maxForce, Math.min(maxForce, swingupforce));
    }
}
class Neuron {
    int[] ls = {5, 16, 8, 1};
    double[][] layer;
    double[][][] w;
    double[][] b;
    double[][] targlayer;
    double[][][] deltaw;
    double[][] dwltab;
    double[][] error;

    public Neuron() {
        int NL = ls.length;
        layer = new double[NL][];
        targlayer = new double[NL][];
        for (int i = 0; i < NL; i++) {
            layer[i] = new double[ls[i]];
            targlayer[i] = new double[ls[i]];
        }
        w = new double[NL - 1][][];
        deltaw = new double[NL - 1][][];
        b = new double[NL - 1][];
        dwltab = new double[NL - 1][];
        error = new double[NL - 1][];
        for (int i = 0; i < NL - 1; i++) {
            int cLS = ls[i];
            int NLS = ls[i + 1];
            w[i] = new double[NLS][cLS];
            deltaw[i] = new double[NLS][cLS];
            b[i] = new double[NLS];
            dwltab[i] = new double[NLS];
            error[i] = new double[NLS];
        }
    }

    void randgen() throws IOException {
        int bc = 0;
        for (int i = 0; i < ls.length - 1; i++) {
            bc = bc + ls[i + 1];
        }
        double alpha;
        FileWriter writer = new FileWriter("weight.txt");
        StringBuilder filecontent = new StringBuilder();
        for (int i = 0; i < ls.length - 1; i++) {
            int s1 = ls[i + 1];
            int s2 = ls[i];
            int k = s1 * s2;
            alpha = Math.sqrt(6.0 / (s1 + s2));
            for (int j = 0; j < k; j++) {
                double v = Math.random() * 2 * alpha - alpha;
                filecontent.append(v).append("\n");
            }
        }
        writer.write(filecontent.toString());
        writer.close();

        FileWriter writer1 = new FileWriter("bias.txt");
        StringBuilder filecontent1 = new StringBuilder();
        alpha = 0.1;
        for (int i = 0; i < bc; i++) {
            double v = Math.random() * alpha * 2 - alpha;
            filecontent1.append(v).append("\n");
        }
        writer1.write(filecontent1.toString());
        writer1.close();
    }

    void setup() throws FileNotFoundException {
        File file1 = new File("weight.txt");
        File file2 = new File("bias.txt");
        Scanner scw = new Scanner(file1);
        Scanner scb = new Scanner(file2);
        for (int i = 0; i < ls.length - 1; i++) {
            int s1 = ls[i + 1];
            int s2 = ls[i];
            for (int j = 0; j < s1; j++) {
                for (int k = 0; k < s2; k++) {
                    w[i][j][k] = scw.nextDouble();
                }
            }
            for (int j = 0; j < s1; j++) {
                b[i][j] = scb.nextDouble();
            }
        }
        scw.close();
        scb.close();
    }

    double neuron_calc(double[] input) {
        neuron_clear();
        normalisation(input);
        for (int i = 0; i < ls[0]; i++) {
            layer[0][i] = input[i];
        }
        for (int n = 0; n < (ls.length - 1); n++) {
            int s1 = ls[n + 1];
            int s2 = ls[n];
            for (int a = 0; a < s1; a++) {
                for (int b = 0; b < s2; b++) {
                    layer[n + 1][a] = layer[n + 1][a] + layer[n][b] * w[n][a][b];
                }
            }
            for (int b = 0; b < s1; b++) {
                if (n == ls.length - 2) {
                    layer[n + 1][b] = Math.tanh(layer[n + 1][b] + this.b[n][b]);
                } else {
                    layer[n + 1][b] = relu(layer[n + 1][b] + this.b[n][b]);
                }
            }
        }
        return layer[ls.length - 1][0];
    }

    void neuron_clear() {
        for (int n = 0; n < (ls.length - 1); n++) {
            int s1 = ls[n + 1];
            for (int a = 0; a < s1; a++) {
                layer[n + 1][a] = 0.0;
            }
        }
    }

    void print() {
        System.out.println(":::::::::::::::::weight");
        for (int i = 0; i < ls.length - 1; i++) {
            int s1 = ls[i + 1];
            int s2 = ls[i];
            for (int j = 0; j < s1; j++) {
                for (int k = 0; k < s2; k++) {
                    System.out.println(w[i][j][k]);
                }
            }
        }
        System.out.println(":::::::::::::::::bias");
        for (int i = 0; i < ls.length - 1; i++) {
            int s1 = ls[i + 1];
            for (int j = 0; j < s1; j++) {
                System.out.println(b[i][j]);
            }
        }
    }

    double k = 1.0;

    void fuckingbackpropogayson(double target, double alpha) {
        int L = ls.length - 2;
        double out = layer[L + 1][0];
        error[L][0] = out * (1.0 - out) * (target - out);
        for (int n = L - 1; n >= 0; n--) {
            int s1 = ls[n + 1];
            int s2 = ls[n + 2];
            for (int j = 0; j < s1; j++) {
                double sum = 0;
                for (int kk = 0; kk < s2; kk++) {
                    sum += w[n + 1][kk][j] * error[n + 1][kk];
                }
                double a = layer[n + 1][j];
                error[n][j] = (a > 0 ? 1.0 : 0.01) * sum;
            }
        }
        for (int n = 0; n <= L; n++) {
            int s1 = ls[n + 1];
            int s2 = ls[n];
            for (int j = 0; j < s1; j++) {
                for (int kk = 0; kk < s2; kk++) {
                    deltaw[n][j][kk] = alpha * layer[n][kk] * error[n][j];
                    w[n][j][kk] += deltaw[n][j][kk];
                }
                dwltab[n][j] = alpha * error[n][j];
                b[n][j] += dwltab[n][j];
            }
        }
    }
    double relu(double x) {
        return x > 0 ? x : 0.01 * x;
    }

    double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    void normalisation(double[] data) {
        data[0] = alpha(data[0]);
        data[1] = alpha(data[1]);
        data[4] = alpha(data[4]);
    }

    private double alpha(double k) {
        k = Math.log(10.0 * k + Math.sqrt(100.0 * k * k + 1.0));
        return k;
    }
}
class Neuron_fucker { // pretrained by rl noo need for training again trust
    int[] ls = {4, 1};
    double[][] layer;
    double[][][] w;
    double[][] b;
    public Neuron_fucker() {
        int NL = ls.length;
        layer = new double[NL][];
        for (int i = 0; i < NL; i++) {
            layer[i] = new double[ls[i]];
        }
        w = new double[NL - 1][][];
        b = new double[NL - 1][];
        for (int i = 0; i < NL - 1; i++) {
            int cLS = ls[i];
            int NLS = ls[i + 1];
            w[i] = new double[NLS][cLS];
            b[i] = new double[NLS];
        }

        double K1 = -1.1623;
        double K2 = -5.0075;
        double K3 = 55.3019;
        double K4 = 9.1142;
        w[0][0][0] = -K1;
        w[0][0][1] = -K2;
        w[0][0][2] = -K3;
        w[0][0][3] = -K4;
        b[0][0] = 0.0;
    }
    double neuron_calc(double[] input) {
        double error = input[2] - Math.PI;
        error = Math.atan2(Math.sin(error), Math.cos(error));
        double[] processed = new double[4];
        processed[0] = input[0]; 
        processed[1] = input[1]; 
        processed[2] = error;    
        processed[3] = input[3]; 
        neuron_clear();
        for (int i = 0; i < ls[0]; i++) {
            layer[0][i] = processed[i];
        }
        for (int n = 0; n < ls.length - 1; n++) {
            int s1 = ls[n + 1];
            int s2 = ls[n];
            for (int a = 0; a < s1; a++) {
                for (int b = 0; b < s2; b++) {
                    layer[n + 1][a] += layer[n][b] * w[n][a][b];
                }
            }
            for(int b = 0; b < s1; b++) {
                layer[n + 1][b] += this.b[n][b];
            }
        }
        return layer[ls.length - 1][0];
    }
    void neuron_clear() {
        for (int n = 0; n < ls.length - 1; n++) {
            int s1 = ls[n + 1];
            for (int a = 0; a < s1; a++) {
                layer[n + 1][a] = 0.0;
            }
        }
    }
    private double relu(double x) {
        return x > 0 ? x : 0.01 * x;
    }
}