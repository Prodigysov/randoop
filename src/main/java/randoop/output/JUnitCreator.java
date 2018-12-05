package randoop.output;

import static randoop.output.NameGenerator.numDigits;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.TokenMgrError;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.VoidType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import randoop.BugInRandoopException;
import randoop.Globals;
import randoop.sequence.ExecutableSequence;

/** Creates Java source as {@code String} for a suite of JUnit4 tests. */
public class JUnitCreator {

  private final String packageName;

  /**
   * classMethodCounts maps test class names to the number of methods in each class. This is used to
   * generate lists of method names for a class, since current convention is that a test method is
   * named "test"+i for some integer i.
   */
  private Map<String, Integer> classMethodCounts;

  /** The Java text for BeforeAll method of generated test class. */
  private BlockStmt beforeAllBody = null;

  /** The Java text for AfterAll method of generated test class. */
  private BlockStmt afterAllBody = null;

  /** The Java text for BeforeEach method of generated test class. */
  private BlockStmt beforeEachBody = null;

  /** The Java text for AfterEach method of generated test class. */
  private BlockStmt afterEachBody = null;

  /** The JUnit annotation for the BeforeAll option */
  private static final String BEFORE_ALL = "BeforeClass";

  /** The JUnit annotation for the AfterAll option */
  private static final String AFTER_ALL = "AfterClass";

  /** The JUnit annotation for the BeforeEach option */
  private static final String BEFORE_EACH = "Before";

  /** The JUnit annotation for the AfterEach option */
  private static final String AFTER_EACH = "After";

  /** The method name for the BeforeAll option */
  private static final String BEFORE_ALL_METHOD = "setupAll";

  /** The method name for the AfterAll option */
  private static final String AFTER_ALL_METHOD = "teardownAll";

  /** The method name for the BeforeEach option */
  private static final String BEFORE_EACH_METHOD = "setup";

  /** The method name for the AfterEach option */
  private static final String AFTER_EACH_METHOD = "teardown";

  public static JUnitCreator getTestCreator(
      String junit_package_name,
      BlockStmt beforeAllBody,
      BlockStmt afterAllBody,
      BlockStmt beforeEachBody,
      BlockStmt afterEachBody) {
    assert !Objects.equals(junit_package_name, "");
    JUnitCreator junitCreator = new JUnitCreator(junit_package_name);
    if (beforeAllBody != null) {
      junitCreator.addBeforeAll(beforeAllBody);
    }
    if (afterAllBody != null) {
      junitCreator.addAfterAll(afterAllBody);
    }
    if (beforeEachBody != null) {
      junitCreator.addBeforeEach(beforeEachBody);
    }
    if (afterEachBody != null) {
      junitCreator.addAfterEach(afterEachBody);
    }
    return junitCreator;
  }

  private JUnitCreator(String packageName) {
    assert !Objects.equals(packageName, "");
    this.packageName = packageName;
    this.classMethodCounts = new LinkedHashMap<>();
  }

  /**
   * Add text for BeforeClass-annotated method in each generated test class.
   *
   * @param body the (Java) text for method
   */
  private void addBeforeAll(BlockStmt body) {
    this.beforeAllBody = body;
  }

  /**
   * Add text for AfterClass-annotated method in each generated text class.
   *
   * @param body the (Java) text for method
   */
  private void addAfterAll(BlockStmt body) {
    this.afterAllBody = body;
  }

  /**
   * Add text for Before-annotated method in each generated test class.
   *
   * @param body the (Java) text for method
   */
  private void addBeforeEach(BlockStmt body) {
    this.beforeEachBody = body;
  }

  /**
   * Add text for After-annotated method in each generated test class.
   *
   * @param text the (Java) text for method
   */
  private void addAfterEach(BlockStmt text) {
    this.afterEachBody = text;
  }

  public CompilationUnit createTestClass(
      String testClassName, String testMethodPrefix, List<ExecutableSequence> sequences) {
    this.classMethodCounts.put(testClassName, sequences.size());

    CompilationUnit compilationUnit = new CompilationUnit();
    if (packageName != null) {
      compilationUnit.setPackage(new PackageDeclaration(new NameExpr(packageName)));
    }

    List<ImportDeclaration> imports = new ArrayList<>();
    if (afterEachBody != null) {
      imports.add(new ImportDeclaration(new NameExpr("org.junit.After"), false, false));
    }
    if (afterAllBody != null) {
      imports.add(new ImportDeclaration(new NameExpr("org.junit.AfterClass"), false, false));
    }
    if (beforeEachBody != null) {
      imports.add(new ImportDeclaration(new NameExpr("org.junit.Before"), false, false));
    }
    if (beforeAllBody != null) {
      imports.add(new ImportDeclaration(new NameExpr("org.junit.BeforeClass"), false, false));
    }
    imports.add(new ImportDeclaration(new NameExpr("org.junit.FixMethodOrder"), false, false));
    imports.add(new ImportDeclaration(new NameExpr("org.junit.Test"), false, false));
    imports.add(
        new ImportDeclaration(new NameExpr("org.junit.runners.MethodSorters"), false, false));
    compilationUnit.setImports(imports);

    // class declaration
    ClassOrInterfaceDeclaration classDeclaration =
        new ClassOrInterfaceDeclaration(Modifier.PUBLIC, false, testClassName);
    List<AnnotationExpr> annotations = new ArrayList<>();
    annotations.add(
        new SingleMemberAnnotationExpr(
            new NameExpr("FixMethodOrder"), new NameExpr("MethodSorters.NAME_ASCENDING")));
    classDeclaration.setAnnotations(annotations);

    List<BodyDeclaration> bodyDeclarations = new ArrayList<>();
    // add debug field
    VariableDeclarator debugVariable = new VariableDeclarator(new VariableDeclaratorId("debug"));
    debugVariable.setInit(new BooleanLiteralExpr(false));
    FieldDeclaration debugField =
        new FieldDeclaration(
            Modifier.PUBLIC | Modifier.STATIC,
            new PrimitiveType(PrimitiveType.Primitive.Boolean),
            debugVariable);
    bodyDeclarations.add(debugField);

    if (beforeAllBody != null) {
      MethodDeclaration fixture =
          createFixture(
              BEFORE_ALL, Modifier.PUBLIC | Modifier.STATIC, BEFORE_ALL_METHOD, beforeAllBody);
      if (fixture != null) {
        bodyDeclarations.add(fixture);
      }
    }
    if (afterAllBody != null) {
      MethodDeclaration fixture =
          createFixture(
              AFTER_ALL, Modifier.PUBLIC | Modifier.STATIC, AFTER_ALL_METHOD, afterAllBody);
      if (fixture != null) {
        bodyDeclarations.add(fixture);
      }
    }
    if (beforeEachBody != null) {
      MethodDeclaration fixture =
          createFixture(BEFORE_EACH, Modifier.PUBLIC, BEFORE_EACH_METHOD, beforeEachBody);
      if (fixture != null) {
        bodyDeclarations.add(fixture);
      }
    }
    if (afterEachBody != null) {
      MethodDeclaration fixture =
          createFixture(AFTER_EACH, Modifier.PUBLIC, AFTER_EACH_METHOD, afterEachBody);
      if (fixture != null) {
        bodyDeclarations.add(fixture);
      }
    }

    NameGenerator methodNameGen =
        new NameGenerator(testMethodPrefix, 1, numDigits(sequences.size()));
    for (ExecutableSequence eseq : sequences) {
      MethodDeclaration testMethod = createTestMethod(testClassName, methodNameGen.next(), eseq);
      if (testMethod != null) {
        bodyDeclarations.add(testMethod);
      }
    }

    // PN: The following commented-out is for adding driver methods in the test class
    // List<MethodDeclaration> driverMethods = createDriverMethods(Collections.singleton(testClassName));
    // bodyDeclarations.addAll(driverMethods);

    classDeclaration.setMembers(bodyDeclarations);
    List<TypeDeclaration> types = new ArrayList<>();
    types.add(classDeclaration);
    compilationUnit.setTypes(types);

    return compilationUnit;
  }

  /**
   * Creates a test method as a {@code String} for the sequence {@code testSequence}.
   *
   * @param className the name of the test class
   * @param methodName the name of the test method
   * @param testSequence the {@link ExecutableSequence} test sequence
   * @return the {@code String} for the test method
   */
  private MethodDeclaration createTestMethod(
      String className, String methodName, ExecutableSequence testSequence) {
    MethodDeclaration method = new MethodDeclaration(Modifier.PUBLIC, new VoidType(), methodName);
    List<AnnotationExpr> annotations = new ArrayList<>();
    annotations.add(new MarkerAnnotationExpr(new NameExpr("Test")));
    method.setAnnotations(annotations);

    List<ReferenceType> throwsList = new ArrayList<>();
    throwsList.add(new ReferenceType(new ClassOrInterfaceType("Throwable")));
    method.setThrows(throwsList);

    BlockStmt body = new BlockStmt();
    List<Statement> statements = new ArrayList<>();
    FieldAccessExpr field = new FieldAccessExpr(new NameExpr("System"), "out");
    MethodCallExpr call = new MethodCallExpr(field, "format");

    // PN: The following commented-out code is for debugging only
    // // >> if (debug) System.out.format("%n%s%n", $className.$methodName); <<
    // List<Expression> arguments = new ArrayList<>();
    // arguments.add(new StringLiteralExpr("%n%s%n"));
    // arguments.add(new StringLiteralExpr(className + "." + methodName));
    // call.setArgs(arguments);
    // statements.add(new IfStmt(new NameExpr("debug"), new ExpressionStmt(call), null));

    // TODO make sequence generate list of JavaParser statements
    String sequenceBlockString = "{ " + testSequence.toCodeString() + " }";
    try {
      BlockStmt sequenceBlock = JavaParser.parseBlock(sequenceBlockString);
      statements.addAll(sequenceBlock.getStmts());
    } catch (ParseException e) {
      System.out.println(
          "Parse error while creating test method " + className + "." + methodName + " for block ");
      System.out.println(sequenceBlockString);
      throw new BugInRandoopException("Parse error while creating test method", e);
    } catch (TokenMgrError e) {
      System.out.println(
          "Lexical error while creating test method " + className + "." + methodName);
      System.out.println("Exception: " + e.getMessage());
      System.out.println(sequenceBlockString);
      return null;
    }

    body.setStmts(statements);
    method.setBody(body);
    return method;
  }

  /**
   * Creates the declaration of a single test fixture.
   *
   * @param annotation the fixture annotation
   * @param modifiers the method modifiers for fixture declaration
   * @param methodName the name of the fixture method
   * @param body the {@code BlockStmt} for the fixture
   * @return the fixture method as a {@code String}
   */
  private MethodDeclaration createFixture(
      String annotation, int modifiers, String methodName, BlockStmt body) {
    MethodDeclaration method = new MethodDeclaration(modifiers, new VoidType(), methodName);
    List<AnnotationExpr> annotations = new ArrayList<>();
    annotations.add(new MarkerAnnotationExpr(new NameExpr(annotation)));
    method.setAnnotations(annotations);
    method.setBody(body);
    return method;
  }

  /**
   * Creates the JUnit4 suite class for the tests in this object as a {@code String}.
   *
   * @param suiteClassName the name of the suite class created
   * @param testClassNames the names of the test classes in the suite
   * @return the {@code String} with the declaration for the suite class
   */
  public String createTestSuite(String suiteClassName, Set<String> testClassNames) {
    CompilationUnit compilationUnit = new CompilationUnit();
    if (packageName != null) {
      compilationUnit.setPackage(new PackageDeclaration(new NameExpr(packageName)));
    }
    List<ImportDeclaration> imports = new ArrayList<>();
    imports.add(new ImportDeclaration(new NameExpr("org.junit.runner.RunWith"), false, false));
    imports.add(new ImportDeclaration(new NameExpr("org.junit.runners.Suite"), false, false));
    compilationUnit.setImports(imports);

    ClassOrInterfaceDeclaration suiteClass =
        new ClassOrInterfaceDeclaration(Modifier.PUBLIC, false, suiteClassName);
    List<AnnotationExpr> annotations = new ArrayList<>();
    annotations.add(
        new SingleMemberAnnotationExpr(new NameExpr("RunWith"), new NameExpr("Suite.class")));
    StringBuilder classList = new StringBuilder();
    Iterator<String> testIterator = testClassNames.iterator();
    if (testIterator.hasNext()) {
      String classCode = testIterator.next() + ".class";
      while (testIterator.hasNext()) {
        classList.append(classCode).append(", ");
        classCode = testIterator.next() + ".class";
      }
      classList.append(classCode);
    }
    annotations.add(
        new SingleMemberAnnotationExpr(
            new NameExpr("Suite.SuiteClasses"), new NameExpr("{ " + classList + " }")));
    suiteClass.setAnnotations(annotations);
    List<TypeDeclaration> types = new ArrayList<>();
    types.add(suiteClass);
    compilationUnit.setTypes(types);
    return compilationUnit.toString();
  }

  /**
   * Create non-reflective test driver as a main class.
   *
   * @param driverName the name for the driver class
   * @param testClassNames the names of the test classes in the suite
   * @return the test driver class as a {@code String}
   */
  public String createTestDriver(String driverName, Set<String> testClassNames) {
    CompilationUnit compilationUnit = new CompilationUnit();
    if (packageName != null) {
      compilationUnit.setPackage(new PackageDeclaration(new NameExpr(packageName)));
    }

    List<ImportDeclaration> imports = new ArrayList<>();
    compilationUnit.setImports(imports);

    List<MethodDeclaration> driverMethods = createDriverMethods(testClassNames);
    List<BodyDeclaration> bodyDeclarations = new ArrayList<>(driverMethods);

    ClassOrInterfaceDeclaration driverClass =
        new ClassOrInterfaceDeclaration(Modifier.PUBLIC, false, driverName);
    driverClass.setMembers(bodyDeclarations);

    List<TypeDeclaration> types = new ArrayList<>();
    types.add(driverClass);
    compilationUnit.setTypes(types);
    return compilationUnit.toString();
  }

  // Generates a main method and several sub methods, that invokes the tests in test classes.
  private List<MethodDeclaration> createDriverMethods(Set<String> testClassNames) {
    // >> public static void main(String... args) <<
    MethodDeclaration mainMethod =
        new MethodDeclaration(Modifier.PUBLIC | Modifier.STATIC, new VoidType(), "main");
    List<Parameter> parameters = new ArrayList<>();
    Parameter parameter =
        new Parameter(new ClassOrInterfaceType("String"), new VariableDeclaratorId("args"));
    parameter.setVarArgs(true);
    parameters.add(parameter);
    mainMethod.setParameters(parameters);

    mainMethod.getParameters().get(0).setVarArgs(true);

    List<Statement> bodyStatements = new ArrayList<>();

    // Reusable variables
    VariableDeclarator variableDecl;
    List<VariableDeclarator> variableList;
    VariableDeclarationExpr variableExpr;

    // Consider each test method isolated
    List<Pair<String, String>> testClassMethodNames = new ArrayList<>();
    for (String testClassName : testClassNames) {
      int classMethodCount = classMethodCounts.get(testClassName);
      NameGenerator methodGen = new NameGenerator("test", 1, numDigits(classMethodCount));
      while (methodGen.nameCount() < classMethodCount) {
        String testMethodName = methodGen.next();
        testClassMethodNames.add(Pair.of(testClassName, testMethodName));
      }
    }

    // >> int totalTests = #$testClassMethodNames; <<
    String totalTestsVariableName = "totalTests";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(totalTestsVariableName),
            new IntegerLiteralExpr(Integer.toString(testClassMethodNames.size())));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(new PrimitiveType(PrimitiveType.Primitive.Int), variableList);
    bodyStatements.add(new ExpressionStmt(variableExpr));

    // >> int limitTests = totalTests; <<
    String limitTestsVariableName = "limitTests";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(limitTestsVariableName), new NameExpr(totalTestsVariableName));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(new PrimitiveType(PrimitiveType.Primitive.Int), variableList);
    bodyStatements.add(new ExpressionStmt(variableExpr));

    // >> boolean isIsolated = true; <<
    String isIsolatedVariableName = "isIsolated";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(isIsolatedVariableName), new BooleanLiteralExpr(true));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(
            new PrimitiveType(PrimitiveType.Primitive.Boolean), variableList);
    bodyStatements.add(new ExpressionStmt(variableExpr));

    // >> int threadId = -1; <<
    String threadIdVariableName = "threadId";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(threadIdVariableName),
            new IntegerLiteralExpr(Integer.toString(-1)));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(new PrimitiveType(PrimitiveType.Primitive.Int), variableList);
    bodyStatements.add(new ExpressionStmt(variableExpr));

    // >> if (args != null) { <<
    IfStmt ifStmtArgs = new IfStmt();
    ifStmtArgs.setCondition(
        new BinaryExpr(new NameExpr("args"), new NullLiteralExpr(), BinaryExpr.Operator.notEquals));

    List<Statement> guardIfThenStatements = new ArrayList<>();

    // >> if (args.length >= 1) <<
    // >>   limitTests = Integer.valueOf(args[0]); <<
    IfStmt ifStmtArgsLength =
        new IfStmt(
            new BinaryExpr(
                new FieldAccessExpr(new NameExpr("args"), "length"),
                new IntegerLiteralExpr(Integer.toString(1)),
                BinaryExpr.Operator.greaterEquals),
            new ExpressionStmt(
                new AssignExpr(
                    new NameExpr(limitTestsVariableName),
                    new MethodCallExpr(
                        new NameExpr("Integer"),
                        "valueOf",
                        Collections.singletonList(
                            new ArrayAccessExpr(
                                new NameExpr("args"),
                                new IntegerLiteralExpr(Integer.toString(0))))),
                    AssignExpr.Operator.assign)),
            null);
    guardIfThenStatements.add(ifStmtArgsLength);

    // >> if (args.length >= 2) <<
    // >>   isIsolated = Boolean.valueOf(args[1]); <<
    ifStmtArgsLength =
        new IfStmt(
            new BinaryExpr(
                new FieldAccessExpr(new NameExpr("args"), "length"),
                new IntegerLiteralExpr(Integer.toString(2)),
                BinaryExpr.Operator.greaterEquals),
            new ExpressionStmt(
                new AssignExpr(
                    new NameExpr(isIsolatedVariableName),
                    new MethodCallExpr(
                        new NameExpr("Boolean"),
                        "valueOf",
                        Collections.singletonList(
                            new ArrayAccessExpr(
                                new NameExpr("args"),
                                new IntegerLiteralExpr(Integer.toString(1))))),
                    AssignExpr.Operator.assign)),
            null);
    guardIfThenStatements.add(ifStmtArgsLength);

    // >> if (args.length >= 3) <<
    // >>   threadId = Integer.valueOf(args[2]); <<
    ifStmtArgsLength =
        new IfStmt(
            new BinaryExpr(
                new FieldAccessExpr(new NameExpr("args"), "length"),
                new IntegerLiteralExpr(Integer.toString(3)),
                BinaryExpr.Operator.greaterEquals),
            new ExpressionStmt(
                new AssignExpr(
                    new NameExpr(threadIdVariableName),
                    new MethodCallExpr(
                        new NameExpr("Integer"),
                        "valueOf",
                        Collections.singletonList(
                            new ArrayAccessExpr(
                                new NameExpr("args"),
                                new IntegerLiteralExpr(Integer.toString(2))))),
                    AssignExpr.Operator.assign)),
            null);
    guardIfThenStatements.add(ifStmtArgsLength);

    // >> } // end if (args != null) <<
    BlockStmt guardIfThenBlock = new BlockStmt(guardIfThenStatements);
    ifStmtArgs.setThenStmt(guardIfThenBlock);
    bodyStatements.add(ifStmtArgs);

    // Create test drivers
    String testDriverMethodName = "testDriver";
    String testIdVariableName = "testId";
    NameGenerator instanceNameGen = new NameGenerator("t");
    List<MethodDeclaration> testDriverMethods =
        createTestDriverLevel(
            0,
            0,
            new ArrayList<>(testClassMethodNames),
            testDriverMethodName,
            instanceNameGen,
            testIdVariableName);

    // >> boolean hasFailure = false; <<
    String failureVariableName = "hadFailure";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(failureVariableName), new BooleanLiteralExpr(false));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(
            new PrimitiveType(PrimitiveType.Primitive.Boolean), variableList);
    bodyStatements.add(new ExpressionStmt(variableExpr));

    // (template<$i> run_test)
    // >> try { $testDriverMethod($i % #$testClassMethodNames); } catch (Throwable e) { hasFailure = true; } <<
    Function<String, Statement> tryTestDriverMethodTemplate =
        i ->
            new TryStmt(
                new BlockStmt(
                    Collections.singletonList(
                        new ExpressionStmt(
                            new MethodCallExpr(
                                null,
                                testDriverMethodName,
                                Collections.singletonList(
                                    new BinaryExpr(
                                        new NameExpr(i),
                                        new IntegerLiteralExpr(
                                            Integer.toString(testClassMethodNames.size())),
                                        BinaryExpr.Operator.remainder)))))),
                Collections.singletonList(
                    new CatchClause(
                        new Parameter(
                            new ClassOrInterfaceType("Throwable"), new VariableDeclaratorId("e")),
                        new BlockStmt(
                            Stream.of(
                                    new ExpressionStmt(
                                        new AssignExpr(
                                            new NameExpr(failureVariableName),
                                            new BooleanLiteralExpr(true),
                                            AssignExpr.Operator.assign)))
                                .collect(Collectors.toList())))),
                null);

    // >> if (isIsolated) { <<
    IfStmt ifStmtIsIsolated = new IfStmt();
    ifStmtIsIsolated.setCondition(new NameExpr(isIsolatedVariableName));

    // >>   if (threadId < limitTests) { <<
    IfStmt ifStmtThreadId = new IfStmt();
    ifStmtThreadId.setCondition(
        new BinaryExpr(
            new NameExpr(threadIdVariableName),
            new NameExpr(limitTestsVariableName),
            BinaryExpr.Operator.less));

    // >>     $run_test(threadId); <<
    ifStmtThreadId.setThenStmt(
        new BlockStmt(
            Collections.singletonList(tryTestDriverMethodTemplate.apply(threadIdVariableName))));

    ifStmtIsIsolated.setThenStmt(new BlockStmt(Collections.singletonList(ifStmtThreadId)));

    // >> } else {
    // >>   for (i=0; i<limitTests; ++i) {
    ForStmt forStmt = new ForStmt();
    String forVariable = "i";
    variableDecl =
        new VariableDeclarator(
            new VariableDeclaratorId(forVariable), new IntegerLiteralExpr(Integer.toString(0)));
    variableList = new ArrayList<>();
    variableList.add(variableDecl);
    variableExpr =
        new VariableDeclarationExpr(new PrimitiveType(PrimitiveType.Primitive.Int), variableList);
    forStmt.setInit(Collections.singletonList(variableExpr));

    forStmt.setCompare(
        new BinaryExpr(
            new NameExpr(forVariable),
            new NameExpr(limitTestsVariableName),
            BinaryExpr.Operator.less));

    forStmt.setUpdate(
        Collections.singletonList(
            new UnaryExpr(new NameExpr(forVariable), UnaryExpr.Operator.preIncrement)));

    // >>     $run_test(i); <<
    forStmt.setBody(
        new BlockStmt(Collections.singletonList(tryTestDriverMethodTemplate.apply(forVariable))));

    ifStmtIsIsolated.setElseStmt(new BlockStmt(Collections.singletonList(forStmt)));
    bodyStatements.add(ifStmtIsIsolated);

    // >> if (hasFailure) { System.exit(1); } <<
    BlockStmt exitCall = new BlockStmt();
    List<Expression> args = new ArrayList<>();
    args.add(new IntegerLiteralExpr("1"));
    List<Statement> exitStatement = new ArrayList<>();
    exitStatement.add(new ExpressionStmt(new MethodCallExpr(new NameExpr("System"), "exit", args)));
    exitCall.setStmts(exitStatement);
    bodyStatements.add(new IfStmt(new NameExpr(failureVariableName), exitCall, null));

    BlockStmt body = new BlockStmt();
    body.setStmts(bodyStatements);
    mainMethod.setBody(body);
    testDriverMethods.add(mainMethod);
    return testDriverMethods;
  }

  private static final int MAX_TESTS_IN_ONE_METHOD = 10;
  private static final double LOG10_MAX_TESTS_IN_ONE_METHOD = Math.log10(MAX_TESTS_IN_ONE_METHOD);

  // For create sub methods of the test driver, which uses a tree structure to find the correct test to invoke.
  // Each method has a switch statement which has at most {@link #MAX_TESTS_IN_ONE_METHOD} branches.
  private List<MethodDeclaration> createTestDriverLevel(
      int testIdBeg,
      int level,
      List<Pair<String, String>> testClassMethodNames,
      String methodName,
      NameGenerator instanceNameGen,
      String testIdVariableName) {
    List<MethodDeclaration> testDriverMethods = new ArrayList<>();
    MethodDeclaration thisLevelMethod =
        new MethodDeclaration(Modifier.PUBLIC | Modifier.STATIC, new VoidType(), methodName);
    List<Parameter> parameters =
        Collections.singletonList(
            new Parameter(
                new ClassOrInterfaceType("int"), new VariableDeclaratorId(testIdVariableName)));
    thisLevelMethod.setParameters(parameters);
    thisLevelMethod.setThrows(
        Collections.singletonList(new ReferenceType(new ClassOrInterfaceType("Throwable"))));

    List<Statement> bodyStatements = new ArrayList<>();
    SwitchStmt switchStmt = new SwitchStmt();
    List<SwitchEntryStmt> switchEntryStatements = new ArrayList<>();

    int testId = testIdBeg;

    if (testClassMethodNames.size() > MAX_TESTS_IN_ONE_METHOD) {
      // test size > MAX_TESTS_IN_ONE_METHOD: switch-branch to next level
      int testsPerMethodNextLevel =
          (int)
              Math.pow(
                  MAX_TESTS_IN_ONE_METHOD,
                  Math.ceil(
                      Math.log10(testClassMethodNames.size()) / LOG10_MAX_TESTS_IN_ONE_METHOD - 1));

      // >> switch (testId / ?testsPerMethodNextLevel) { <<
      switchStmt.setSelector(
          new BinaryExpr(
              new NameExpr(testIdVariableName),
              new IntegerLiteralExpr(Integer.toString(testsPerMethodNextLevel)),
              BinaryExpr.Operator.divide));

      ArrayList<Pair<String, String>> testClassNameArrayList =
          new ArrayList<>(testClassMethodNames);
      int nextLevelIdx = 0;
      NameGenerator nextLevelTestDriverNameGen = new NameGenerator(methodName + "_");
      while (testId - testIdBeg < testClassNameArrayList.size()) {
        List<Pair<String, String>> nextLevelTestClassMethodNames =
            testClassNameArrayList.subList(
                testId - testIdBeg,
                Math.min(
                    testId - testIdBeg + testsPerMethodNextLevel, testClassNameArrayList.size()));
        String nextLevelMethodName = nextLevelTestDriverNameGen.next();
        List<MethodDeclaration> nextLevelMethods =
            createTestDriverLevel(
                testId,
                level + 1,
                nextLevelTestClassMethodNames,
                nextLevelMethodName,
                instanceNameGen,
                testIdVariableName);
        testDriverMethods.addAll(nextLevelMethods);

        // >> case ?testIdBeg/?testsPerMethodNextLevel + ?nextLevelIdx: nextLevelMethodName(testId); return; <<
        SwitchEntryStmt switchEntryStmt =
            new SwitchEntryStmt(
                new IntegerLiteralExpr(
                    Integer.toString(testIdBeg / testsPerMethodNextLevel + nextLevelIdx)),
                Stream.of(
                        new ExpressionStmt(
                            new MethodCallExpr(
                                null,
                                nextLevelMethodName,
                                Collections.singletonList(new NameExpr(testIdVariableName)))),
                        new ReturnStmt())
                    .collect(Collectors.toList()));
        switchEntryStatements.add(switchEntryStmt);
        nextLevelIdx++;
        testId += testsPerMethodNextLevel;
      }
    } else {
      // Reusable variables
      VariableDeclarator variableDecl;
      List<VariableDeclarator> variableList;
      VariableDeclarationExpr variableExpr;

      // >> switch (testId) { <<
      switchStmt.setSelector(new NameExpr(testIdVariableName));

      for (Pair<String, String> testClassMethodName : testClassMethodNames) {
        String testClassName = testClassMethodName.getLeft();
        String testMethodName = testClassMethodName.getRight();

        // tests size <= MAX_TESTS_IN_ONE_METHOD: generate all tests in a switch-branch
        SwitchEntryStmt switchEntryStmt = new SwitchEntryStmt();

        // >> case $0: <<
        switchEntryStmt.setLabel(new IntegerLiteralExpr(Integer.toString(testId)));

        List<Statement> caseStatements = new ArrayList<>();

        // BeforeAll
        if (beforeAllBody != null) {
          caseStatements.add(
              new ExpressionStmt(
                  new MethodCallExpr(new NameExpr(testClassName), BEFORE_ALL_METHOD)));
        }

        // >> RegressionTest$0 t$0 = new RegressionTest$0(); <<
        String testVariable = instanceNameGen.next();
        variableDecl =
            new VariableDeclarator(
                new VariableDeclaratorId(testVariable),
                new ObjectCreationExpr(null, new ClassOrInterfaceType(testClassName), null));
        variableList = new ArrayList<>();
        variableList.add(variableDecl);
        variableExpr =
            new VariableDeclarationExpr(new ClassOrInterfaceType(testClassName), variableList);
        caseStatements.add(new ExpressionStmt(variableExpr));

        // BeforeEach
        if (beforeEachBody != null) {
          caseStatements.add(
              new ExpressionStmt(
                  new MethodCallExpr(new NameExpr(testVariable), BEFORE_EACH_METHOD)));
        }

        // PN: The following commented-out code is for debugging only
        // // >> System.out.println("t$0"); <<
        // caseStatements.add(
        //     new ExpressionStmt(new MethodCallExpr(new NameExpr("System.out"), "println",
        //         Collections.singletonList(new StringLiteralExpr("t" + testId)))));

        // >> t$0.test$(($0+1))(); <<
        caseStatements.add(
            new ExpressionStmt(new MethodCallExpr(new NameExpr(testVariable), testMethodName)));

        // AfterEach
        if (afterEachBody != null) {
          caseStatements.add(
              new ExpressionStmt(
                  new MethodCallExpr(new NameExpr(testVariable), AFTER_EACH_METHOD)));
        }

        // AfterAll
        if (afterAllBody != null) {
          caseStatements.add(
              new ExpressionStmt(
                  new MethodCallExpr(new NameExpr(testClassName), AFTER_ALL_METHOD)));
        }

        // >> return; <<
        caseStatements.add(new ReturnStmt());

        switchEntryStmt.setStmts(caseStatements);
        switchEntryStatements.add(switchEntryStmt);
        testId++;
      }
    }

    // >> end switch <<
    switchStmt.setEntries(switchEntryStatements);
    bodyStatements.add(switchStmt);

    BlockStmt body = new BlockStmt();
    body.setStmts(bodyStatements);
    thisLevelMethod.setBody(body);
    testDriverMethods.add(thisLevelMethod);
    return testDriverMethods;
  }

  public static BlockStmt parseFixture(List<String> bodyText) throws ParseException {
    if (bodyText == null) {
      return null;
    }
    StringBuilder blockText = new StringBuilder();
    blockText.append("{").append(Globals.lineSep);
    for (String line : bodyText) {
      blockText.append(line).append(Globals.lineSep);
    }
    blockText.append(Globals.lineSep).append("}");
    return JavaParser.parseBlock(blockText.toString());
  }
}
