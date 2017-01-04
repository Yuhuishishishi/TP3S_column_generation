package facility;

import algorithm.Algorithm;
import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import ilog.concert.*;
import ilog.cp.IloCP;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 12/14/2016.
 * University of Michigan
 * Academic use only
 */
public class LastIterationSolverCP implements Algorithm {

    private List<Column> colPool;

    private final long timeInit;

    public LastIterationSolverCP(List<ColumnWithTiming> generatedCols) {
        this.colPool = extractSeqInfo(generatedCols);
        timeInit = System.currentTimeMillis();
    }

    private List<Column> extractSeqInfo(List<ColumnWithTiming> generatedCols) {
        Set<Column> seqInfo = generatedCols.stream().map(columnWithTiming -> new Column(columnWithTiming.getInstID(),
                columnWithTiming.getSeq(), columnWithTiming.getRelease())).collect(Collectors.toSet());
        return new ArrayList<>(seqInfo);
    }

    private void solveLastIteration() throws IloException {
        // partition the colPool;
        Map<String, List<Column>> colListByInstID = new HashMap<>();
        DataInstance.getInstIds().forEach(instID-> colListByInstID.put(instID, new ArrayList<>()));
        colPool.forEach(col->colListByInstID.get(col.getInstID()).add(col));

        // solve using CP algorithm

        IloCP model = new IloCP();

        // variables
        Map<String, IloIntVar[]> useCol;
        Map<String, IloIntervalVar[]> prep, tat, analysis, dur;
        useCol = new HashMap<>();
        prep = new HashMap<>();
        tat = new HashMap<>();
        analysis = new HashMap<>();
        dur = new HashMap<>();

        // initialization
        for (String instID : DataInstance.getInstIds()) {
            final int numCols = colListByInstID.get(instID).size();
            final List<TestRequest> testRequestArr = DataInstance.getInstance(instID).getTestArr();
            final int numTests = testRequestArr.size();
            IloIntVar[] instUseCol;
            IloIntervalVar[] instPrep = new IloIntervalVar[numTests];
            IloIntervalVar[] instTat = new IloIntervalVar[numTests];
            IloIntervalVar[] instAnalysis = new IloIntervalVar[numTests];
            IloIntervalVar[] instDur = new IloIntervalVar[numTests];

            instUseCol = model.boolVarArray(numCols);
            for (int i = 0; i < numTests; i++) {
                instPrep[i] = model.intervalVar(testRequestArr.get(i).getPrep());
                instTat[i] = model.intervalVar(testRequestArr.get(i).getTat());
                instAnalysis[i] = model.intervalVar(testRequestArr.get(i).getAnalysis());
                instDur[i] = model.intervalVar();
            }

            useCol.put(instID, instUseCol);
            prep.put(instID, instPrep);
            tat.put(instID, instTat);
            analysis.put(instID, instAnalysis);
            dur.put(instID, instDur);
        }

        // global resource constraint
        IloCumulFunctionExpr resource = model.cumulFunctionExpr();
        List<IloNumExpr> tardinessExprList = new ArrayList<>();

        for (String instID : DataInstance.getInstIds()) {
            final List<TestRequest> testRequestArr = DataInstance.getInstance(instID).getTestArr();

            for (int i = 0; i < testRequestArr.size(); i++) {
                IloIntervalVar prepVar = prep.get(instID)[i];
                IloIntervalVar tatVar = tat.get(instID)[i];
                IloIntervalVar analysisVar = analysis.get(instID)[i];
                IloIntervalVar durVar = dur.get(instID)[i];

                // span constraints
                model.add(model.span(durVar, new IloIntervalVar[]{
                        prepVar, tatVar, analysisVar
                }));

                // prep -> tat -> analysis
                model.add(model.endAtStart(prepVar, tatVar));
                model.add(model.endAtStart(tatVar, analysisVar));

                // time window constraints
                tatVar.setStartMin(testRequestArr.get(i).getRelease());

                //  resource
                resource.add(model.pulse(tatVar, 1));

                // tardiness
                tardinessExprList.add(model.max(0,
                        model.diff(testRequestArr.get(i).getDeadline(), model.endOf(durVar))));
            }

        }

        // cover each test

        // vehicle capacity constraints

        // resource constraints
        model.add(model.le(resource, Global.FACILITY_CAP));

    }

    @Override
    public void solve() {







    }

    @Override
    public long getTimeTillNow() {
        return (System.currentTimeMillis()-timeInit);
    }
}
