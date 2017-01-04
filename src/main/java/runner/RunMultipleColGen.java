package runner;

import algorithm.Algorithm;
import algorithm.multiple.MultipleColumnGenerationFacility;
import data.DataInstance;
import data.Reader;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by yuhuishi on 1/3/2017.
 * University of Michigan
 * Academic use only
 */
public class RunMultipleColGen {

    public static void main(String[] args) {
        String small_small = "C:\\Users\\yuhuishi\\PycharmProjects\\instance_generator\\instance\\multiple\\small_small";

        String out = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\multiple\\small_small\\";

//        run(small_small, out);

        String small_moderate = "C:\\Users\\yuhuishi\\PycharmProjects\\instance_generator\\instance\\multiple\\small_moderate";
        out = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\multiple\\small_moderate\\";

        run(small_moderate, out);

        String moderate_moderate = "C:\\Users\\yuhuishi\\PycharmProjects\\instance_generator\\instance\\multiple\\moderate_moderate";
        out = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\multiple\\moderate_moderate\\";

        run(moderate_moderate, out);


    }

    private static void run(String instDir, String outDir) {

        try {
            File dir = new File(instDir);
            File[] files = dir.listFiles();

            Algorithm colgenSolver;

            if (files != null) {
                for (File child : files) {
                    System.out.println("Start solving w/o facility version " + child.getPath());
                    String outPath = child.getName().replace("tp3s", "log");
                    PrintStream ps = new PrintStream(outDir + outPath);
                    System.setOut(ps);

                    Reader jsonReader = new Reader(child.getAbsolutePath());
                    DataInstance.init(jsonReader);
                    colgenSolver = new MultipleColumnGenerationFacility();

                    long time = System.nanoTime();

                    colgenSolver.solve();

                    System.out.println("Time spent " + (System.nanoTime() - time) / 1e6 + "ms");

                    ps.close();
                }
            } else {
                // Handle the case where dir is not really a directory.
                // Checking dir.isDirectory() above would not be sufficient
                // to avoid race conditions with another process that deletes
                // directories.
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
