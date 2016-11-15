package drools1359;

import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang.StringUtils;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.junit.Test;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;


public class TestRaceCondition {

  @Test
  public void testRaceCondition() {
    KnowledgeBuilder knowledgeBuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

      List<String> lines = new ArrayList<>();
      lines.add("package testrules");
      lines.add("import static " + Helper.class.getName() + ".*");
      lines.add("import " + Fact.class.getName());
      lines.add("rule \"test-rule\" ");
      lines.add("  when ");
      lines.add("    fact : Fact( staticMethod(arg) )");
      lines.add("  then ");
      lines.add("    fact.setActivated(); ");
      lines.add("end");
      String drl = StringUtils.join(lines, "\n");
      knowledgeBuilder.add(ResourceFactory.newReaderResource(new StringReader(drl)), ResourceType.DRL);

      if (knowledgeBuilder.getErrors().size() > 0) {
        for (KnowledgeBuilderError error : knowledgeBuilder.getErrors()) {
          throw new RuntimeException("DRL from rule registers errors:\n" + "\nError:\n" + error);
        }
      }

    final KnowledgeBaseImpl knowledgeBase = (KnowledgeBaseImpl) KnowledgeBaseFactory.newKnowledgeBase();
    knowledgeBase.addKnowledgePackages(knowledgeBuilder.getKnowledgePackages());

    int numberOfThreads = 2;

    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    Callable<Boolean> executeRulesTask = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {

        // Create a knowledge session
        KieSession session = knowledgeBase.newKieSession();
        try {
          // Insert the fact objects
          session.insert(new Fact());

          // Run the session
          session.fireAllRules();

          return true;
        } catch (RuntimeException e) {
          e.printStackTrace();
          return false;
        } finally {
          session.dispose();
        }
      }
    };

    List<Future<Boolean>> executionResults = new ArrayList<>();

    for (int i = 0; i < numberOfThreads; i++)
      executionResults.add(executor.submit(executeRulesTask));

    executor.shutdown();

    boolean finalResult = true;
    for (int i = 0; i < numberOfThreads; i++) {
      try {
        finalResult &= executionResults.get(i).get();
      } catch (InterruptedException | ExecutionException e) {
        finalResult = false;
      }
    }
    assertTrue(finalResult);
  }
}
