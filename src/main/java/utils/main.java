package utils;

import algorithm.Algorithm;
import data.DataInstance;
import data.Reader;
import facility.ColumnGenerationFacilityCPLEX;

/**
 * Created by yuhuishi on 10/5/2016.
 * University of Michigan
 * Academic use only
 */
public class Main {

    public static void main(String[] args) {
        int id = 0;
        if (args.length==0) {
            System.err.println("Please provide the id for the instance");
            System.exit(0);
        }
        else
            id = Integer.parseInt(args[0]);

        String basePath = "./data/";
        String filePath = basePath + String.valueOf(id) + " - orig.tp3s";

        Reader jsonReader = new Reader(filePath);
        DataInstance.init(jsonReader);

        long time = System.nanoTime();

        Algorithm solver = new ColumnGenerationFacilityCPLEX();
        solver.solve();

        System.out.println("Time spent " + (System.nanoTime()-time)/1e6 + "ms");

    }

}
