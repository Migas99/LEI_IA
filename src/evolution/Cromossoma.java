package evolution;

import impl.Point;
import impl.UIConfiguration;
import interf.IPoint;
import interf.IUIConfiguration;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import viewer.PathViewer;

public class Cromossoma implements Comparable<Cromossoma> {

    private UIConfiguration conf;

    private int maxMap = 600;
    private int minMap = 0;
    private int collisionNumber = 0;

    private int collisionWeight = 9999;

    private double totaldist;

    public List<IPoint> points = new ArrayList<>();
    public List<Rectangle> rectangles;

    public Cromossoma(UIConfiguration conf) {
        this.conf = conf;

        //obter obstaculos
        rectangles = conf.getObstacles();

        //adicionar ponto de inicio
        points.add(conf.getStart());
        this.starting();

        //adicionar ponto de fim
        points.add(conf.getEnd());
        totaldist = 0.0;
    }

    public Cromossoma(boolean child, UIConfiguration conf) { //Para as mutações
        //obter obstaculos
        this.conf = conf;
        rectangles = this.conf.getObstacles();
        totaldist = 0.0;
    }

    /**
     * começa a gerar caminho
     * verifica se ao conectar o ponto de inicio com o ponto de fim interseta o obstáculo
     * se intersetar cria um novo ponto até o ponto antigo não intersetar o novo
     * depois guarda o novo ponto e se a ligação do novo ponto com o ponto de fim intersetar
     * o obstáculo volta a fazer o mesmo ciclo
     */

    public void starting() {
        if (lineIntersects((Point) points.get(0), (Point) conf.getEnd())) {
            Point lastPoint = (Point) points.get(0);
            Point newPoint;
            int x;
            int y;
            do {
                do {
                    x = new Random().nextInt((maxMap - minMap) + 1) + minMap;
                    y = new Random().nextInt((maxMap - minMap) + 1) + minMap;
                    newPoint = new Point(x, y);
                } while (lineIntersects(lastPoint, newPoint));
                points.add(newPoint);
                lastPoint = newPoint;
            }while(lineIntersects(lastPoint, (Point) conf.getEnd()));
        }
    }

    public Cromossoma mutate() {
        Cromossoma novo = new Cromossoma(conf);
        novo.points.clear();

        for (int z = 0; z < points.size(); z++) {
            novo.points.add(this.points.get(z));
        }

        Random random = new Random();
        int newx;
        int newy;

        if (this.collisionChecker()) {
            if (points.size() > 3)  {
                int rand;
                do {
                    rand = random.nextInt((points.size() - 2));
                    newx = new Random().nextInt((maxMap - minMap) + 1) + minMap;
                    newy = new Random().nextInt((maxMap - minMap) + 1) + minMap;
                } while (newx <= 0 || newy <= 0 || newx >= 600 || newy >= 600 || rand == 0);
                Point newpoint = new Point(newx, newy);
                novo.points.set(rand, newpoint);
            }

        } else {
            if (points.size() > 3) {
                int rand;
                do {
                    rand = random.nextInt((points.size() - 1));
                    newx = points.get(rand).getX() + ThreadLocalRandom.current().nextInt((int) -Conf.mutation_rate, (int) Conf.mutation_rate);
                    newy = points.get(rand).getY() + ThreadLocalRandom.current().nextInt((int) -Conf.mutation_rate, (int) Conf.mutation_rate);
                } while (newx <= 0 || newy <= 0 || newx >= 600 || newy >= 600 || rand == 0);
                Point newpoint = new Point(newx, newy);
                novo.points.set(rand, newpoint);
            }
        }
        return novo;
    }

    /**
     * Cruzamento trocando os valores de x e y dos "pais"
     */
    public Cromossoma[] cross(Cromossoma other) {

        Cromossoma filho1 = new Cromossoma(true, this.conf), filho2 = new Cromossoma(true, this.conf);
        filho1.points.add(conf.getStart());
        filho2.points.add(conf.getStart());

        int sizePai = this.points.size();
        int sizeMae = other.points.size();
        int minSize;
        if (sizeMae > sizePai) {
            minSize = sizePai;
        } else {
            minSize = sizeMae;
        }

        //logica filho1
        for (int i = 1; i < minSize - 1; i++) {
            int x = this.points.get(i).getX() + other.points.get(i).getX();
            int y = this.points.get(i).getY() + other.points.get(i).getY();
            filho1.points.add(new Point(x / 2, y / 2));
        }

        //logica filho 2
        for (int i = 1; i < minSize - 1; i++) {
            int x = this.points.get(i).getX() + other.points.get(i).getX();
            int y = this.points.get(i).getY() + other.points.get(i).getY();
            filho2.points.add(new Point(y / 2, x / 2));
        }

        filho1.points.add(conf.getEnd());
        filho2.points.add(conf.getEnd());

        Cromossoma[] novos = {filho1, filho2};

        return novos;
    }

    //Imprime os pontos
    public String[] givePoints() {
        Point point;
        Point lastPoint;
        String[] lines = new String[points.size()];
        for (int w = 1; w < this.points.size(); w++) {
            point = (Point) this.points.get(w);
            lastPoint = (Point) this.points.get(w - 1);
            lines[w - 1] = (lastPoint.getX() + "; " + point.getX() + " | " + lastPoint.getY() + "; " + point.getY()) + " | ";
            System.out.println("Ponto " + w + " { " + (lastPoint.getX() + "; " + point.getX() + " | " + lastPoint.getY() + "; " + point.getY()) + " }");
        }
        return lines;
    }

    public List<IPoint> getPoints() {
        return points;
    }

    //Verifica se existe alguma colisão
    public boolean collisionChecker() {
        boolean result = false;
        collisionNumber = 0;
        for (int t = 1; t < this.points.size(); t++) {
            Point point = (Point) this.points.get(t);
            Point lastPoint = (Point) this.points.get(t - 1);
            Line2D line2d = new Line2D.Double(lastPoint.getX(), lastPoint.getY(), point.getX(), point.getY());

            for (int j = 0; j < this.rectangles.size(); j++) {
                boolean collision = line2d.intersects(this.rectangles.get(j));
                if (collision) {
                    result = true;
                    collisionNumber++;
                }
            }
        }
        return result;
    }

    //check if a line between two points intersects a obstacle
    public boolean lineIntersects(Point initialPoint, Point finalPoint) {
        Line2D line2d = new Line2D.Double(initialPoint.getX(), initialPoint.getY(), finalPoint.getX(), finalPoint.getY());
        for (int p = 0; p < this.rectangles.size(); p++) {
            if (line2d.intersects(this.rectangles.get(p))) {
                return true;
            }
        }
        return false;
    }

    public double getTotalDistance() {
        totaldist = 0.0;
        for (int y = 1; y < this.points.size(); y++) {
            Point point = (Point) this.points.get(y);
            Point lastPoint = (Point) this.points.get(y - 1);
            totaldist = totaldist + Math.sqrt((point.getY() - lastPoint.getY()) * (point.getY() - lastPoint.getY()) + (point.getX() - lastPoint.getX()) * (point.getX() - lastPoint.getX()));
        }
        return totaldist;
    }

    /**
     * Verifica se existe colisões, se existir atribui o peso da colisão por cada colisão
     * Depois adiciona ao valor a distancia mais 100 vezes o número de pontos
     */
    public double getFitness() {
        double fitness = 0.0;

        if (collisionChecker()) {
            fitness = collisionWeight * collisionNumber;
        }

        fitness = fitness + getTotalDistance() + (100*points.size());

        return fitness;
    }

    //cria o mapa
    public void map() {
        PathViewer pv = new PathViewer(conf);
        pv.setFitness(getFitness());
        pv.setStringPath(Arrays.toString(givePoints()));
        pv.paintPath(points);
    }

    /**
     * Para permitir ordenar utilizando a API de Java
     *
     * @param o
     * @return
     */
    @Override
    public int compareTo(Cromossoma o) {
        if (o.getFitness() < this.getFitness())
            return 1;
        else if (o.getFitness() > this.getFitness())
            return -1;
        else return 0;
    }

    @Override
    public String toString() {
        return "Fitness: " + getFitness()
                +"\nDistância: "+getTotalDistance()
                + "\nPontos: ";
    }
}