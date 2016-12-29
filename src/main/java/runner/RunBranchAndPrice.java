package runner;

import algorithm.Algorithm;
import branchandprice.BranchAndPrice;
import data.DataInstance;
import data.Reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * Created by yuhuishi on 12/27/2016.
 * University of Michigan
 * Academic use only
 */
public class RunBranchAndPrice {

    public static void main(String[] args) {


        // moderate
        String filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\moderate";
        run(filePath, "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\branchandprice\\moderate");

        // small
//        String filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\small";
//        run(filePath, "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\branchandprice\\small");
//
//
//        // large
        filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\large";
        run(filePath, "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\branchandprice\\large");
//
//        String filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\small\\_20_16_0.8_1.0.tp3s";
//        runOneInstance(filePath);
    }

    private static void run(String instDir, String outDir) {

            File dir = new File(instDir);
            File[] files = dir.listFiles();

            BranchAndPrice branchAndPriceSolver;

            if (files != null) {
                for (File child : files) {
                    if (child.isDirectory())
                        continue;
                    System.out.println("Running branch and price " + child.getPath());
                    String outPath = child.getName().replace("tp3s", "log");
                    PrintStream ps = null;
                    try {
                        ps = new PrintStream(outDir + outPath);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    System.setOut(ps);

                    Reader jsonReader = new Reader(child.getAbsolutePath());
                    DataInstance.init(jsonReader);
                    branchAndPriceSolver = BranchAndPrice.getBranchTree();

                    long time = System.nanoTime();

                    branchAndPriceSolver.solve();

                    System.out.println("Time spent " + (System.nanoTime() - time) / 1e6 + "ms");

                    ps.close();
                    branchAndPriceSolver.destroy();
                }
            } else {
                // Handle the case where dir is not really a directory.
                // Checking dir.isDirectory() above would not be sufficient
                // to avoid race conditions with another process that deletes
                // directories.
            }
    }

    private static void runOneInstance(String instanceFilePath) {
        Reader jsonReader = new Reader(instanceFilePath);
        DataInstance.init(jsonReader);
        Algorithm branchAndPriceSolver = BranchAndPrice.getBranchTree();

        long time = System.nanoTime();

        branchAndPriceSolver.solve();

        System.out.println("Time spent " + (System.nanoTime() - time) / 1e6 + "ms");
    }
}
