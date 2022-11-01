/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sunarvrm;
    // TODO: osetrit 0 hodnot
    // if (cnt == 0) return Double.NaN;

/**
 * Counts basic numerical statistics
 * @author chmelarp
 */
public class StatisticsScalar  {
    private long cnt = 0;
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;
    private double sum = 0;
    private double sum2 = 0;

    public long add(double x) {
        sum += x;
        sum2 += x*x;

        if (x < min) min = x;
        if (x > max) max = x;

        return ++cnt;
    }

    public long count() {
        return cnt;
    }

    public double avg() {
        return sum/cnt;
    }

    public double std() {
        return Math.sqrt(sum2/cnt - this.avg()*this.avg());
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double sum() {
        return sum;
    }
/*
    public static void test() {
        StatisticsScalar sn = new StatisticsScalar();

        java.util.Random rand = new java.util.Random(System.nanoTime());
        for (int i = 1; i <= 10; i++) {
             double r = rand.nextDouble()*100;
             System.out.println(i +": "+ r);
             sn.add(r);
        }

        System.out.println("cnt: "+ sn.count());
        System.out.println("avg: "+ sn.avg());
        System.out.println("std: "+ sn.std());
        System.out.println("max: "+ sn.max());
        System.out.println("min: "+ sn.min());
  }
*/
}
