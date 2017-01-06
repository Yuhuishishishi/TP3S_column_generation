import algorithm.Algorithm;
import algorithm.multiple.MultipleColumnGenerationFacility;
import data.DataInstance;
import data.Reader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by yuhuishi on 1/2/2017.
 * University of Michigan
 * Academic use only
 */
public class MultipleInstTest {

    @Before
    public void readData() {
        final String filePath = "C:\\Users\\yuhuishi\\PycharmProjects\\instance_generator\\instance\\multiple\\small_moderate\\s77_m49.tp3s";
        Reader reader = new Reader(filePath);
        DataInstance.init(reader);
    }

    @Test
    public void testMultipleReader() {
        assert DataInstance.getInstance()==null;
        assert DataInstance.getInstIds().size()==2;
        assert DataInstance.getInstIds().contains("s13")
                && DataInstance.getInstIds().contains("s45");
    }

    @Test
    public void testMultipleColumnGeneration() {
        Algorithm solver = new MultipleColumnGenerationFacility();
        solver.solve();

    }
}
