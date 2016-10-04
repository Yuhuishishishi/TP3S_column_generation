import algorithm.Algorithm;
import algorithm.Column;
import algorithm.ColumnGeneration;
import algorithm.ColumnGenerationCplex;
import data.DataInstance;
import data.Reader;
import facility.ColumnGenerationFacility;
import org.junit.Test;

import java.util.List;


/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class FullEnumTest {

    private final String filepath = "./data/157 - orig.tp3s";

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
    public void testFullEnumAlgoCPLEX() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm fullEnumSolver = new ColumnGenerationCplex();
        ((ColumnGenerationCplex) fullEnumSolver).solveFull();
        assert fullEnumSolver != null;
    }

    @Test
    public void testColGenAlgoCPLEX() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm fullEnumSolver = new ColumnGenerationCplex();
        fullEnumSolver.solve();
        assert fullEnumSolver != null;
    }

    @Test
    public void testColGenWithoutFacility() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm colgenSolver = new ColumnGeneration();

        long time = System.nanoTime();

        colgenSolver.solve();

        System.out.println("Time spent " + (System.nanoTime()-time)/1e6 + "ms");
    }


    @Test
    public void testFullEnumWithFacility() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        Algorithm fullEnumSolver = new ColumnGenerationFacility();
        long time = System.nanoTime();

        fullEnumSolver.solve();
        System.out.println("Time spent " + (System.nanoTime()-time)/1e6 + "ms");

        assert fullEnumSolver != null;
    }

}
