package runner;

import algorithm.Algorithm;
import algorithm.ColumnGeneration;
import data.DataInstance;
import data.Reader;
import facility.ColumnGenerationFacility;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by yuhuishi on 12/22/2016.
 * University of Michigan
 * Academic use only
 */
public class RunNoFacilityVersion {

    public static void main(String[] args) {
        String instanceDir = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\small";
        String outDir = "./logs/fullenum/small";

        // small size
//        run(instanceDir, outDir, true);
//        runFullEnum(instanceDir, outDir);

//        outDir = "./logs/small/";
//        run(instanceDir, outDir, false);

        // moderate
        instanceDir = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\moderate";
        outDir = "./logs/fullenum/moderate";
//        run(instanceDir, outDir, false);
        runFullEnum(instanceDir, outDir);

//
//        outDir = "./logs/facility/moderate/";
//        run(instanceDir, outDir, true);


        // large
        instanceDir = "C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\instance\\large";
        outDir = "./logs/fullenum/large";

        runFullEnum(instanceDir, outDir);
//        run(instanceDir, outDir, false);

//        outDir = "./logs/facility/large/";
//        run(instanceDir, outDir, true);



    }

    private static void run(String instDir, String outDir, boolean isFacilityVersion) {

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
                    if (!isFacilityVersion)
                        colgenSolver = new ColumnGeneration();
                    else
                        colgenSolver = new ColumnGenerationFacility();

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

    private static void runFullEnum(String instDir, String outDir) {

        try {
            File dir = new File(instDir);
            File[] files = dir.listFiles();

            ColumnGeneration fullEnumSolver = new ColumnGeneration();

            if (files != null) {
                for (File child : files) {
                    if (child.isDirectory())
                        continue;

                    System.out.println("Start solving with Full Enumeration algorithm " + child.getPath());
                    String outPath = child.getName().replace("tp3s", "log");
                    PrintStream ps = new PrintStream(outDir + outPath);
                    System.setOut(ps);

                    Reader jsonReader = new Reader(child.getAbsolutePath());
                    DataInstance.init(jsonReader);

                    long time = System.nanoTime();

                    fullEnumSolver.solveFull();

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
