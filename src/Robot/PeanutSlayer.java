package Robot;

import evolution.GeneticAlgorithm;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import impl.Point;
import impl.UIConfiguration;
import interf.IPoint;
import performance.EvaluateFire;
import robocode.Robot;
import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PeanutSlayer extends AdvancedRobot {

    EvaluateFire ef;

    /*
     * lista de obstáculos, preenchida ao fazer scan
     * */
    private List<Rectangle> obstacles;
    public static UIConfiguration conf;
    private List<IPoint> points;
    private HashMap<String, Double> inimigos;
    private HashMap<String, Rectangle> inimigosRect;//utilizada par associar inimigos a retângulos e permitir remover retângulos de inimigos já desatualizados
    private EasyPredictModelWrapper model;

    //variável que contém o ponto atual para o qual o robot se está a dirigir
    private int currentPoint = -1;

    /* How many times we have decided to not change direction. */
    public int sameDirectionCounter = 0;

    /* How long we should continue to move in the current direction */
    public long moveTime = 1;

    /* The direction we are moving in */
    public int moveDirection = 1;

    /* The speed of the last bullet that hit us, used in determining how far to move before deciding to change direction again. */
    public double lastBulletSpeed = 15.0;

    public double wallStick = 120;

    private boolean isFiring = false;

    @Override
    public void run() {

        ef = new EvaluateFire("PeanutSlayers");

        try {
            obstacles = new ArrayList<>();
            inimigosRect = new HashMap<>();
            inimigos = new HashMap<>();
            conf = new UIConfiguration((int) getBattleFieldWidth(), (int) getBattleFieldHeight(), obstacles);
            model = new EasyPredictModelWrapper(MojoModel.load("H2OModels/DRF_04.zip"));

            /* Set some crazy colors! */
            setBodyColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setGunColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setRadarColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setBulletColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setScanColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));

            setAdjustGunForRobotTurn(true);
            setAdjustRadarForGunTurn(true);

            /* Loop forever */

            /* Simple Radar Code */
            while (true) {
                    if (getRadarTurnRemaining() == 0.0) {
                        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
                }

                //se se está a dirigir para algum ponto
                if (currentPoint >= 0) {
                    IPoint ponto = points.get(currentPoint);
                    //se já está no ponto ou lá perto...
                    if (utils.Utils.getDistance(this, ponto.getX(), ponto.getY()) < 2) {
                        currentPoint++;
                        //se chegou ao fim do caminho
                        if (currentPoint >= points.size())
                            currentPoint = -1;
                    }

                    advancedRobotGoTo(this, ponto.getX(), ponto.getY());
                }

                execute();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        super.onHitByBullet(e);
        this.lastBulletSpeed = e.getVelocity();
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        super.onBulletHit(event);
        ef.addHit(event);
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        super.onRoundEnded(event);
    }

    @Override
    public void onBattleEnded(BattleEndedEvent event) {
        super.onBattleEnded(event);
        ef.submit(event.getResults());
    }

    @Override
    public void onMouseClicked(MouseEvent e) {
        super.onMouseClicked(e);

        this.clearAllEvents();

        conf.setStart(new impl.Point((int) this.getX(), (int) this.getY()));
        conf.setEnd(new Point(e.getX(), e.getY()));

        GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(conf);
        this.points = geneticAlgorithm.run().points;

        currentPoint = 0;
    }

    @Override
    public void onPaint(Graphics2D g) {
        super.onPaint(g);

        g.setColor(Color.RED);
        obstacles.stream().forEach(x -> g.drawRect(x.x, x.y, (int) x.getWidth(), (int) x.getHeight()));

        if (points != null) {
            for (int i = 1; i < points.size(); i++)
                drawThickLine(g, points.get(i - 1).getX(), points.get(i - 1).getY(), points.get(i).getX(), points.get(i).getY(), 2, Color.green);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        super.onScannedRobot(e);

        ef.addScanned(e);

        /* For effect only, doing this every turn could cause seizures. This makes it change every 32 turns. */
        if (e.getTime() % 32 == 0) {
            /* Set some crazy colors! */
            setBodyColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setGunColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setRadarColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setBulletColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setScanColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
        }

        //DRAW RECTANGLE AROUND ENEMY
        Point2D.Double ponto = getEnemyCoordinates(this, e.getBearing(), e.getDistance());
        ponto.x -= this.getWidth() * 2.5 / 2;
        ponto.y -= this.getHeight() * 2.5 / 2;

        Rectangle rect = new Rectangle((int) ponto.x, (int) ponto.y, (int) (this.getWidth() * 2.5), (int) (this.getHeight() * 2.5));

        //se já existe um retângulo deste inimigo
        if (inimigosRect.containsKey(e.getName())) {
            //remover da lista de retângulos
            obstacles.remove(inimigosRect.get(e.getName()));
        } else {
            inimigos.put(e.getName(), e.getEnergy());
        }

        obstacles.add(rect);
        inimigosRect.put(e.getName(), rect);

        //FIRE
        RowData row = new RowData();
        row.put("robotName", e.getName());
        row.put("energy", e.getEnergy());
        row.put("bearing", e.getBearing());
        row.put("distance", e.getDistance());
        row.put("robotHeading", e.getHeading());
        row.put("gunHeading", this.getGunHeading());
        row.put("velocity", e.getVelocity());
        boolean willFire = false;

        double absBearing = e.getBearingRadians() + getHeadingRadians();

        try {
            BinomialModelPrediction prediction = model.predictBinomial(row);
            willFire = Boolean.parseBoolean(prediction.label.toLowerCase());
            System.out.println(willFire);
        } catch (PredictException exception) {
            exception.printStackTrace();
        }


        if(!this.isFiring){
            /* Gun */
            double bulletPower = 1.0 + Math.random() * 2.0;
            double bulletSpeed = 20 - 3 * bulletPower;

            /* Aim at a random offset in the general direction the enemy is heading. */
            double enemyLatVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
            System.out.println(e.getName());
            double escapeAngle = Math.asin(8.0 / bulletSpeed);

            /* Signum produces 0 if it is not moving, meaning we will fire directly head on at an unmoving target */
            double enemyDirection = Math.signum(enemyLatVel);
            double angleOffset = escapeAngle * enemyDirection * Math.random();

            if (willFire && Math.abs(this.getGunTurnRemaining()) == 0) {
                turnGunRightRadians(Utils.normalRelativeAngle(absBearing + angleOffset - getGunHeadingRadians()));
                /* Adding this if so it does not kill itself by firing. */
                if (getEnergy() > bulletPower) {
                    System.out.println(e.getName());
                    this.fireBullet(bulletPower);
                }
            }
            else{
                //TRY DODGE BULLETS
                double previousEnergy = inimigos.get(e.getName());
                double changeInEnergy = previousEnergy - e.getEnergy();
                inimigos.remove(inimigos.get(e.getName()));
                inimigos.put(e.getName(), e.getEnergy());
                if (changeInEnergy != 0) {
                    System.out.println("RUN NIGGA RUN");
                    setTurnRight(e.getBearing() + 90);
                    this.ahead(50.0 * moveDirection);
                }
            }
        }
    }

    @Override
    public Bullet fireBullet(double power){

        this.isFiring = true;

        Bullet bullet = super.fireBullet(power);

        this.isFiring = false;

        return bullet;
    }

    //@Override
    public void onScannedRobot2(ScannedRobotEvent e) {
        /* For effect only, doing this every turn could cause seizures. This makes it change every 32 turns. */
        if (e.getTime() % 32 == 0) {
            /* Set some crazy colors! */
            setBodyColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setGunColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setRadarColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setBulletColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));
            setScanColor(new Color((float) Math.random(), (float) Math.random(), (float) Math.random()));

            /* Change the wall stick distance, to make us even more unpredictable */
            wallStick = 120 + Math.random() * 40;
        }


        double absBearing = e.getBearingRadians() + getHeadingRadians();
        double distance = e.getDistance() + (Math.random() - 0.5) * 5.0;

        /* Radar Turn */
        double radarTurn = Utils.normalRelativeAngle(absBearing
                // Subtract current radar heading to get turn required
                - getRadarHeadingRadians());

        double baseScanSpan = (18.0 + 36.0 * Math.random());
        // Distance we want to scan from middle of enemy to either side
        double extraTurn = Math.min(Math.atan(baseScanSpan / distance), Math.PI / 4.0);
        setTurnRadarRightRadians(radarTurn + (radarTurn < 0 ? -extraTurn : extraTurn));

        /* Movement */
        if (--moveTime <= 0) {
            distance = Math.max(distance, 100 + Math.random() * 50) * 1.25;
            moveTime = 50 + (long) (distance / lastBulletSpeed);

            ++sameDirectionCounter;

            /* Determine if we should change direction */
            if (Math.random() < 0.5 || sameDirectionCounter > 16) {
                moveDirection = -moveDirection;
                sameDirectionCounter = 0;
            }
        }


        /* Move perpendicular to our enemy, based on our movement direction */
        double goalDirection = absBearing - Math.PI / 2.0 * moveDirection;

        /* This is too clean for crazy! Add some randomness. */
        goalDirection += (Math.random() - 0.5) * (Math.random() * 2.0 + 1.0);

        /* Smooth around the walls, if we smooth too much, reverse direction! */
        double x = getX();
        double y = getY();
        double smooth = 0;

        /* Calculate the smoothing we would end up doing if we actually smoothed walls. */
        Rectangle2D fieldRect = new Rectangle2D.Double(18, 18, getBattleFieldWidth() - 36, getBattleFieldHeight() - 36);

        while (!fieldRect.contains(x + Math.sin(goalDirection) * wallStick, y + Math.cos(goalDirection) * wallStick)) {
            /* turn a little toward enemy and try again */
            goalDirection += moveDirection * 0.1;
            smooth += 0.1;
        }

        /* If we would have smoothed to much, then reverse direction. */
        /* Add && sameDirectionCounter != 0 check to make this smarter */
        if (smooth > 0.5 + Math.random() * 0.125) {
            moveDirection = -moveDirection;
            sameDirectionCounter = 0;
        }

        double turn = Utils.normalRelativeAngle(goalDirection - getHeadingRadians());

        /* Adjust so we drive backwards if the turn is less to go backwards */
        if (Math.abs(turn) > Math.PI / 2) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            setBack(100);
        } else {
            setAhead(100);
        }

        setTurnRightRadians(turn);

        /* Gun */
        double bulletPower = 1.0 + Math.random() * 2.0;
        double bulletSpeed = 20 - 3 * bulletPower;

        /* Aim at a random offset in the general direction the enemy is heading. */
        double enemyLatVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
        double escapeAngle = Math.asin(8.0 / bulletSpeed);

        /* Signum produces 0 if it is not moving, meaning we will fire directly head on at an unmoving target */
        double enemyDirection = Math.signum(enemyLatVel);
        double angleOffset = escapeAngle * enemyDirection * Math.random();
        setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing + angleOffset - getGunHeadingRadians()));

        /* Adding this if so it does not kill itself by firing. */
        if (getEnergy() > bulletPower) {
            this.fireBullet(bulletPower);
        }
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        moveDirection *= -1;
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        moveDirection *= -1;
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        super.onRobotDeath(event);

        Rectangle rect = inimigosRect.get(event.getName());
        obstacles.remove(rect);
        inimigosRect.remove(event.getName());
        inimigos.remove(event.getName());

        System.out.println("SEUS NOOBS!!!");
    }

    @Override
    public void onWin(WinEvent event){
        super.onWin(event);
        System.out.println("GG EZ");
        System.out.println("NICE TUTORIAL");
    }

    /**
     * Devolve as coordenadas de um alvo
     *
     * @param robot    o meu robot
     * @param bearing  ângulo para o alvo, em graus
     * @param distance distância ao alvo
     * @return coordenadas do alvo
     */
    public static Point2D.Double getEnemyCoordinates(Robot robot, double bearing, double distance) {
        double angle = Math.toRadians((robot.getHeading() + bearing) % 360);

        return new Point2D.Double((robot.getX() + Math.sin(angle) * distance), (robot.getY() + Math.cos(angle) * distance));
    }

    private void drawThickLine(Graphics g, int x1, int y1, int x2, int y2, int thickness, Color c) {

        g.setColor(c);
        int dX = x2 - x1;
        int dY = y2 - y1;

        double lineLength = Math.sqrt(dX * dX + dY * dY);

        double scale = (double) (thickness) / (2 * lineLength);

        double ddx = -scale * (double) dY;
        double ddy = scale * (double) dX;
        ddx += (ddx > 0) ? 0.5 : -0.5;
        ddy += (ddy > 0) ? 0.5 : -0.5;
        int dx = (int) ddx;
        int dy = (int) ddy;

        int xPoints[] = new int[4];
        int yPoints[] = new int[4];

        xPoints[0] = x1 + dx;
        yPoints[0] = y1 + dy;
        xPoints[1] = x1 - dx;
        yPoints[1] = y1 - dy;
        xPoints[2] = x2 - dx;
        yPoints[2] = y2 - dy;
        xPoints[3] = x2 + dx;
        yPoints[3] = y2 + dy;

        g.fillPolygon(xPoints, yPoints, 4);
    }

    /**
     * Dirige o robot (AdvancedRobot) para determinadas coordenadas
     *
     * @param robot o meu robot
     * @param x     coordenada x do alvo
     * @param y     coordenada y do alvo
     */
    public static void advancedRobotGoTo(AdvancedRobot robot, double x, double y) {
        x -= robot.getX();
        y -= robot.getY();

        double angleToTarget = Math.atan2(x, y);
        double targetAngle = robocode.util.Utils.normalRelativeAngle(angleToTarget - Math.toRadians(robot.getHeading()));
        double distance = Math.hypot(x, y);
        double turnAngle = Math.atan(Math.tan(targetAngle));
        robot.setTurnRight(Math.toDegrees(turnAngle));
        if (targetAngle == turnAngle)
            robot.setAhead(distance);
        else
            robot.setBack(distance);
        robot.execute();
    }
}
