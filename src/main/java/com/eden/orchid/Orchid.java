package com.eden.orchid;

import com.caseyjbrooks.clog.Clog;
import com.eden.orchid.compilers.Compiler;
import com.eden.orchid.compilers.ContentCompiler;
import com.eden.orchid.compilers.PreCompiler;
import com.eden.orchid.compilers.SiteCompilers;
import com.eden.orchid.generators.Generator;
import com.eden.orchid.generators.SiteGenerators;
import com.eden.orchid.options.Option;
import com.eden.orchid.options.SiteOptions;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.json.JSONObject;

// TODO: Handle priority clashes
// TODO: create a json data-getter which parses input like 'options.d' and make root private
// TODO: Create a self-start method so that it can be run from within a server. Think: always up-to-date documentation at the same url (www.myjavadocs.com/latest) running from JavaEE, Spring, even Ruby on Rails with JRuby!
public final class Orchid {

// Doclet hackery to allow this to aprse documentation as expected and not crash...
//----------------------------------------------------------------------------------------------------------------------
    public static int optionLength(String option) {
        return SiteOptions.optionLength(option);
    }

    /**
     * NOTE: Without this method present and returning LanguageVersion.JAVA_1_5,
     *       Javadoc will not process generics because it assumes LanguageVersion.JAVA_1_1
     * @return language version (hard coded to LanguageVersion.JAVA_1_5)
     */
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

// Main Doclet
//----------------------------------------------------------------------------------------------------------------------

    private static JSONObject root = new JSONObject();

    private static Theme theme;

    private static RootDoc rootDoc;

    /**
     * Start the Javadoc generation process
     *
     * @param rootDoc  the root of the project to generate sources for
     * @return Whether the generation was successful
     */
    public static boolean start(RootDoc rootDoc) {
        Orchid.rootDoc = rootDoc;

        pluginScan();
        optionsScan(rootDoc);
        if(shouldContinue()) {
            indexingScan(rootDoc);
            generationScan(rootDoc);
            generateHomepage(rootDoc);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Step one: scan the classpath for all classes tagged with @AutoRegister and register them according to their type.
     *
     * If you need to register plugins that are not one of the classes or interfaces defined in Orchid Core, you can
     * register it within a static initializer. Every AutoRegister annotated class has an instance created by its no-arg
     * constructor, so you are guaranteed to run the static initializer of any AutoRegister annotated class.
     */
    private static void pluginScan() {
        FastClasspathScanner scanner = new FastClasspathScanner();
        scanner.matchClassesWithAnnotation(AutoRegister.class, (matchingClass) -> {
            try {
                Clog.d("AutoRegistering class: #{$1}", new Object[]{matchingClass.getName()});
                Object instance = matchingClass.newInstance();

                // Register compilers
                if(instance instanceof Compiler) {
                    Compiler compiler = (Compiler) instance;
                    SiteCompilers.compilers.put(compiler.priority(), compiler);
                }
                if(instance instanceof ContentCompiler) {
                    ContentCompiler compiler = (ContentCompiler) instance;
                    SiteCompilers.contentCompilers.put(compiler.priority(), compiler);
                }
                if(instance instanceof PreCompiler) {
                    PreCompiler compiler = (PreCompiler) instance;
                    SiteCompilers.precompilers.put(compiler.priority(), compiler);
                }

                // Register generators
                else if(instance instanceof Generator) {
                    Generator generator = (Generator) instance;
                    SiteGenerators.generators.put(generator.priority(), generator);
                }

                // Register command-line options
                if(instance instanceof Option) {
                    Option option = (Option) instance;
                    SiteOptions.optionsParsers.put(option.priority(), option);
                }
            }
            catch (IllegalAccessException|InstantiationException e) {
                e.printStackTrace();
            }
        });
        scanner.scan();
    }

    /**
     * Step two: parse all command-line args using the registered Options.
     *
     * @param rootDoc  the root of the project to generate sources for
     */
    private static void optionsScan(RootDoc rootDoc) {
        root.put("options", new JSONObject());
        SiteOptions.parseOptions(rootDoc, root.getJSONObject("options"));
    }

    /**
     * Step three: perform a sanity-check to make sure all required site components have been set.
     */
    private static boolean shouldContinue() {
        boolean shouldContinue = true;

        if(OrchidUtils.isEmpty(query("options.d"))) {
            Clog.e("You MUST define an output directory with the '-d' flag. It should be an absolute directory which will contain all generated files.");
            shouldContinue = false;
        }

        if(theme == null) {
            Clog.e("You MUST define a theme with the '-theme` flag. It should be the fully-qualified class name of the desired theme.");
            shouldContinue = false;
        }
        else {
            if(SiteCompilers.getContentCompiler(theme.getContentCompilerClass()) == null) {
                Clog.e("Your selected theme's content compiler could not be found.");
                shouldContinue = false;
            }

            for(Class<? extends Compiler> compiler : theme.getRequiredCompilers()) {
                if(SiteCompilers.getCompiler(compiler) == null) {
                    Clog.e("Your selected theme depends on a compiler that could not be found: #{$1}.", new Object[]{compiler.getName()});
                    shouldContinue = false;
                }
            }

            if(!OrchidUtils.isEmpty(theme.getMissingOptions())) {
                Clog.e("Your selected theme depends on the following command line options that could not be found: -#{$1 | join(', -') }.", new Object[] {theme.getMissingOptions()});
                shouldContinue = false;
            }
        }

        if(OrchidUtils.isEmpty(query("options.resourcesDir"))) {
            Clog.w("You should consider defining source resources with the '-resourcesDir' flag to customize the final styling or add additional content to your Javadoc site. It should be the absolute path to a folder containing your custom resources.");
        }

        return shouldContinue;
    }

    /**
     * Step four: scan all registered generators and index all discovered components. No content should be written at
     * this point, we are just gathering the references to files that will be written, so that when we start writing
     * files we can be sure we are able to generate links to any other piece of generated content.
     *
     * @param rootDoc  the root of the project to generate sources for
     */
    private static void indexingScan(RootDoc rootDoc) {
        root.put("index", new JSONObject());
        SiteGenerators.startIndexing(rootDoc, root.getJSONObject("index"));
    }

    /**
     * Step five: scan all registered generators and generate the final output files. At this point, any file that will
     * be generated should be able to be linked to finding its location within the index.
     *
     * @param rootDoc  the root of the project to generate sources for
     */
    private static void generationScan(RootDoc rootDoc) {
        root.put("generators", new JSONObject());
        SiteGenerators.startGeneration(rootDoc, root.getJSONObject("generators"));
    }

    /**
     * Step six: generate the final site homepage
     *
     * @param rootDoc  the root of the project to generate sources for
     */
    private static void generateHomepage(RootDoc rootDoc) {
        root.put("root", new JSONObject(root.toString()));
        theme.generateHomepage(rootDoc, root);
    }

    /**
     * Query the gathered site data using a javascript-like syntax, or the native JSONObject query syntax. For example,
     * given a JSONObject initialized with this document:
     * <pre>
     * {
     *   "a": {
     *     "b": "c"
     *   }
     * }
     * </pre>
     * and this JSONPointer string:
     * <pre>
     * "/a/b"
     * </pre>
     * or this Javascript pointer string:
     * <pre>
     * "a.b"
     * </pre>
     * Then this method will return the String "c".
     * In the end, the Javascript syntax is converted to the corresponding JSONPointer syntax and queried.
     *
     * @param pointer  string that can be used to create a JSONPointer
     * @return  the item matched by the JSONPointer, otherwise null
     */
    public static JSONElement query(String pointer) {
        if(OrchidUtils.isEmpty(pointer)) {
            return new JSONElement(root);
        }

        pointer = pointer.replaceAll("\\.", "/");

        if(!pointer.startsWith("/")) {
            pointer = "/" + pointer;
        }

        Object object = root.query(pointer);

        if(object != null) {
            try {
                return new JSONElement(object);
            }
            catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Gets the root JSONObject of all data gathered. In most cases, it is preferable to just query for the data you
     * need with the Orchid#query() metho.
     *
     * @return  the root JSONObject
     */
    public static JSONObject getRoot() {
        return root;
    }

    /**
     * Gets the currenty set theme
     *
     * @return  the currenty set theme
     */
    public static Theme getTheme() {
        return theme;
    }

    /**
     * Sets the theme to use for the site generation process.
     *
     * @param theme  the theme to set
     */
    public static void setTheme(Theme theme) {
        Orchid.theme = theme;
    }

    public static ClassDoc findClass(String className) {
        return rootDoc.classNamed(className);
    }

    public static RootDoc getRootDoc() {
        return rootDoc;
    }

    //    private static int selfStart() {
//        //https://github.com/arafalov/Solr-Javadoc/blob/master/JavadocIndex/Indexer/src/com/solrstart/javadoc/indexer/Index.java#L224
//        return Main.execute("JavadocIndexer", "com.solrstart.javadoc.indexer.Index", new String[]{
//                "-sourcepath", sourcepath,
//                "-subpackages", subpackages
//        });
//    }
}