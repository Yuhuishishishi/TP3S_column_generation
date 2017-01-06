package facility;

import algorithm.Algorithm;
import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import ilog.concert.*;
import ilog.cp.IloCP;
import ilog.cp.IloSearchPhase;
import utils.Global;

import java.lang.reflect.Array;
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
            // check if tid == index
            List<Integer> tidList = DataInstance.getInstance(instID).getTidList();
            for (int i = 0; i < tidList.size(); i++) {
                int tid = tidList.get(i);
                assert i==tid;
            }


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
        IloIntExpr totalTardinessExpr = model.intExpr();
        IloIntExpr usedVehicleExpr = model.intExpr();

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
//                tardinessExprList.add(model.max(0,
//                        model.diff(testRequestArr.get(i).getDeadline(), model.endOf(durVar))));
                totalTardinessExpr = model.sum(totalTardinessExpr,
                        model.max(0,
                        model.diff(testRequestArr.get(i).getDeadline(), model.endOf(durVar))));
            }


            final List<Column> columnList = colListByInstID.get(instID);

            Map<Integer, IloIntExpr> colsCoverTest, colsUseVehicle;
            colsCoverTest = new HashMap<>();
            colsUseVehicle = new HashMap<>();
            for (int tid : DataInstance.getInstance(instID).getTidList()) {
                colsCoverTest.put(tid, model.intExpr());
            }
            for (int release : DataInstance.getInstance(instID).getVehicleReleaseList()) {
                colsUseVehicle.put(release, model.intExpr());
            }
            for (int i = 0; i < columnList.size(); i++) {
                Column col = columnList.get(i);
                IloIntVar var = useCol.get(instID)[i];
                usedVehicleExpr = model.sum(usedVehicleExpr, var);
                for (int tid : col.getSeq()) {
                    IloIntExpr expr = colsCoverTest.get(tid);
                    expr = model.sum(expr, var);
                    colsCoverTest.put(tid, expr);
                }

                IloIntExpr expr = colsUseVehicle.get(col.getRelease());
                expr = model.sum(expr, var);
                colsUseVehicle.put(col.getRelease(), expr);

                // start after vehicle is release
                final int firstTid = col.getSeq().get(0);
                final IloIntervalVar durVar = dur.get(instID)[firstTid];
                model.add(model.ifThen(model.eq(var, 1),
                        model.ge(model.startOf(durVar), col.getRelease())));

                // precedence relation between tests
                for (int j = 1; j < col.getSeq().size(); j++) {
                    IloIntervalVar preDurVar = dur.get(instID)[col.getSeq().get(j - 1)];
                    IloIntervalVar currDurVar = dur.get(instID)[col.getSeq().get(j)];
                    model.add(model.ifThen(model.eq(var, 1),
                            model.le(model.endOf(preDurVar), model.startOf(currDurVar))));
                }

            }

            // cover each test
            for (IloIntExpr expr : colsCoverTest.values()) {
                model.add(model.ge(expr, 1));
            }

            // vehicle capacity constraints
            for (Map.Entry<Integer, IloIntExpr> entry : colsUseVehicle.entrySet()) {
                model.add(model.le(entry.getValue(), entry.getKey()));
            }
        }

        // resource constraints
        model.add(model.le(resource, Global.FACILITY_CAP));

        // objective
        model.addMinimize(model.prod(Global.VEHICLE_COST, usedVehicleExpr));


        // searching strategy
        // sequence first, then timing
        List<IloIntVar> selectColList = new ArrayList<>();
        useCol.values().forEach(varArr -> selectColList.addAll(Arrays.asList(varArr)));
        IloIntVar[] selectColArr = selectColList.toArray(new IloIntVar[selectColList.size()]);
        IloSearchPhase seqSelect = model.searchPhase(selectColArr);

        // decide the tat first
        List<IloIntervalVar> tatVarList = new ArrayList<>();
        tat.values().forEach(varArr->tatVarList.addAll(Arrays.asList(varArr)));
        IloIntervalVar[] tatVarArr = tatVarList.toArray(new IloIntervalVar[tatVarList.size()]);
        IloSearchPhase decideTat = model.searchPhase(tatVarArr);

        model.setSearchPhases(new IloSearchPhase[]{seqSelect, decideTat});

        int totalUsedVehicles = 0;
        int totalTardiness = 0;
        if (model.solve()) {
            // parse the solution
            for (String instID : DataInstance.getInstIds()) {
                System.out.println("Instance " + instID + " statistics: ");
                // used vehicles
                IloIntVar[] instCols = useCol.get(instID);

                double[] vals = new double[instCols.length];
                model.getValues(instCols, vals);
                long usedVehicles = Arrays.stream(vals).filter(v -> v > 0.5).count();

                // tardiness
                int tardiness = 0;
                for (int i = 0; i < DataInstance.getInstance(instID).getTidList().size(); i++) {
                    IloIntervalVar durVar = dur.get(instID)[i];
                    final int tid = DataInstance.getInstance(instID).getTidList().get(i);
                    final int deadline = DataInstance.getInstance(instID).getTestById(tid).getDeadline();
                    final int end = model.getEnd(durVar);
                    tardiness += (Math.max(0, end-deadline));
                }

                System.out.println("Used vehicles: " + usedVehicles);
                System.out.println("Tardiness: " + tardiness);

                totalUsedVehicles += usedVehicles;
                totalTardiness += tardiness;
            }
        }

        double objVal = totalUsedVehicles*Global.VEHICLE_COST + totalTardiness;
        System.out.println("Obj val: " + objVal);

        model.end();

    }

    @Override
    public void solve() {

        try {
            solveLastIteration();
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTimeTillNow() {
        return (System.currentTimeMillis()-timeInit);
    }
}
