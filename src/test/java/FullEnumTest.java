import algorithm.Algorithm;
import algorithm.Column;
import algorithm.ColumnGenerationFacility;
import algorithm.ColumnGeneration;
import data.DataInstance;
import data.Reader;
import org.junit.Test;

import java.util.List;


/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class FullEnumTest {

    private String filepath = "C:\\Users\\yuhui\\Desktop\\TP3S_cp\\data\\157 - orig.tp3s";

    @Test
    public void testGetTests() throws Exception {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        List<Column> result = ColumnGeneration.enumInitCol(4);
        assert result.size()>0;
    }

    @Test
    public void testFullEnumAlgo() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm fullEnumSolver = new ColumnGeneration();
        ((ColumnGeneration) fullEnumSolver).solveFull();
        assert fullEnumSolver != null;
    }

    @Test
    public void testColGenWithoutFacility() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm colgenSolver = new ColumnGeneration();
        colgenSolver.solve();
    }


    @Test
    public void testFullEnumWithFacility() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm fullEnumSolver = new ColumnGenerationFacility();
        fullEnumSolver.solve();
        assert fullEnumSolver != null;
    }

}
