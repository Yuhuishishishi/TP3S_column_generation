package runner;

import algorithm.Algorithm;
import data.DataInstance;
import data.Reader;
import integerprogramming.GeneralIntegerProgramming;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * Created by yuhuishi on 12/28/2016.
 * University of Michigan
 * Academic use only
 */
public class RunIntegerProgramming {

    public static void main(String[] args) {

        String filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\data\\156 - orig.tp3s";
        runOneInstance(filePath);


//        // moderate
//        String filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\moderate";
//        run(filePath, "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\integer\\moderate");
//
//        // small
//        filePath = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\small";
//        run(filePath, "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\logs\\integer\\small");
    }

    private static void run(String instDir, String outDir) {

        File dir = new File(instDir);
        File[] files = dir.listFiles();

        Algorithm solver;

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
                solver = new GeneralIntegerProgramming();

                long time = System.nanoTime();

                solver.solve();

                System.out.println("Time spent " + (System.nanoTime() - time) / 1e6 + "ms");

                ps.close();
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
        Algorithm solver = new GeneralIntegerProgramming();

        long time = System.nanoTime();

        solver.solve();

        System.out.println("Time spent " + (System.nanoTime() - time) / 1e6 + "ms");
    }
}
