import algorithm.Column;
import data.DataInstance;
import data.Reader;
import facility.ColumnWithTiming;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yuhuishi on 9/27/2016.
 * University of Michigan
 * Academic use only
 */
public class ColumnEqualTest {

    private final String filepath = "./data/157 - orig.tp3s";


    @Test
    public void columnEqTest() {

        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        List<Integer> seq1 = new ArrayList<>();
        List<Integer> seq2 = new ArrayList<>();
        int[] tids = new int[] {1883,1895,1870};
        for (int i = 0; i < tids.length; i++) {
            seq1.add(tids[i]);
            seq2.add(tids[i]);
        }

        Column col1 = new Column(seq1, 10);
        Column col2 = new Column(seq2, 10);
        Column col3 = new Column(seq1, 2);
        List<Integer> seq3 = new ArrayList<>();
        seq3.add(1883); seq3.add(1870); seq3.add(1895);
        Column col4 = new Column(seq3, 10);


        assert col1.equals(col2);
        assert col1.hashCode()==col2.hashCode();
        assert !col1.equals(col3);
        assert col1.hashCode()!=col3.hashCode();
        assert !col1.equals(col4);
        assert col1.hashCode()!=col4.hashCode();
    }

    @Test
    public void columnWithTimingEqTest() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        List<Integer> seq1 = new ArrayList<>();
        List<Integer> seq2 = new ArrayList<>();
        int[] tids = new int[] {1883,1895,1870};
        Map<Integer,Integer> start1 = new HashMap<>();
        Map<Integer,Integer> start2 = new HashMap<>();


        for (int i = 0; i < tids.length; i++) {
            seq1.add(tids[i]); seq2.add(tids[i]);
            start1.put(tids[i],i); start2.put(tids[i], i);
        }

        ColumnWithTiming col1 = new ColumnWithTiming(seq1, 10, start1);
        ColumnWithTiming col2 = new ColumnWithTiming(seq2, 10, start2);

        assert col1.equals(col2);
        assert col1.hashCode()==col2.hashCode();
    }

}
