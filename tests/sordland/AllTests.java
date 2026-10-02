package sordland;

import java.nio.file.*;

public final class AllTests {
    private AllTests() {
    }
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        JsonChecks.run();
        LoaderChecks.run();
        SourceClassificationChecks.run();
        TypeProjectionChecks.run();
        SemanticChecks.run();
        DialogueChecks.run();
        LayoutChecks.run();
        ViewportChecks.run();
        TextChecks.run();
        ThemeTypographyChecks.run();
        ActorFilterChecks.run();
        EdgeChecks.run();
        Path data = Path.of(args.length == 0 ? "data": args[0]);
        var runtimeDataset = DataChecks.run(data);
        var dataset = new sordland.data.Domain.Dataset(runtimeDataset.items(), runtimeDataset.conversations(), runtimeDataset.ancillary(),
            runtimeDataset.diagnostics(), runtimeDataset.gameFlow(), runtimeDataset.ignoredData());
        GameFlowLoaderChecks.run(data, dataset);
        SourceClassificationChecks.realData(dataset);
        SourceAccountingChecks.run(data, dataset);
        NewsChecks.run(dataset);
        TypeProjectionChecks.realData(dataset);
        RootedCampaignChecks.run(dataset);
        CampaignLayoutChecks.run(dataset);
        DialogueChecks.realData(dataset);
        RuntimeChecks.run(data, runtimeDataset);
        VariableChecks.run(runtimeDataset);
        DialogueGuardChecks.run(runtimeDataset);
        NumericLayoutChecks.run(runtimeDataset);
        RouteCorrectionChecks.run(runtimeDataset);
        RouteDagChecks.run(runtimeDataset);
        FinalValidationChecks.run(runtimeDataset);
        CompactChecks.main(new String[0]);
        TargetReachabilityChecks.main(new String[0]);
        System.out.printf("PASS: %,d checks in %.2f s%n", TestSupport.count(),(System.nanoTime() - start) / 1_000_000_000.0);
    }
}
