package kr.ac.kaist.hybridroid.analysis.string;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import kr.ac.kaist.hybridroid.analysis.FieldDefAnalysis;
import kr.ac.kaist.hybridroid.analysis.resource.AndroidResourceAnalysis;
import kr.ac.kaist.hybridroid.analysis.string.constraint.ConstraintGraph;
import kr.ac.kaist.hybridroid.analysis.string.constraint.ConstraintVisitor;
import kr.ac.kaist.hybridroid.analysis.string.constraint.IBox;
import kr.ac.kaist.hybridroid.analysis.string.constraint.InteractionConstraintMonitor;
import kr.ac.kaist.hybridroid.analysis.string.constraint.VarBox;
import kr.ac.kaist.hybridroid.analysis.string.model.StringModel;
import kr.ac.kaist.hybridroid.callgraph.graphutils.ConstraintGraphVisualizer;
import kr.ac.kaist.hybridroid.callgraph.graphutils.WalaCGVisualizer;
import kr.ac.kaist.hybridroid.util.data.Pair;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.dalvik.ipa.callgraph.impl.AndroidEntryPoint;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator.LocatorFlags;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyClassTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyMethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

/**
 * 
 * @author Sungho Lee
 */
public class AndroidStringAnalysis implements StringAnalysis{
	private AnalysisScope scope;
	private WorkList worklist;
	
	public AndroidStringAnalysis(){
		scopeInit();
		worklist = new WorkList();
	}
	
	public AndroidStringAnalysis(AndroidResourceAnalysis ra){
		this();
		StringModel.setResourceAnalysis(ra, null);
	}
	
	private void scopeInit(){
		scope = AnalysisScope.createJavaAnalysisScope();
		scope.setLoaderImpl(ClassLoaderReference.Primordial, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
	}

	public void setExclusion(String exclusions){
		File exclusionsFile = new File(exclusions);
		try {
			InputStream fs = exclusionsFile.exists() ? new FileInputStream(exclusionsFile) : FileProvider.class.getClassLoader()
			    .getResourceAsStream(exclusionsFile.getName());
			scope.setExclusions(new FileOfClasses(fs));
			fs.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void addAnalysisScope(String path){
		// TODO Auto-generated method stub
		if(path.endsWith(".apk")){
			try {
				scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(new File(path)));
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			throw new InternalError("Support only apk format as target file");	
		}
	}

	public void setupAndroidLibs(String... libs){
		try{
			for(String lib : libs){
				if(lib.endsWith(".dex"))
					scope.addToScope(ClassLoaderReference.Primordial, DexFileModule.make(new File(lib)));
				else if(lib.endsWith(".jar"))
					scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(new JarFile(new File(lib))));
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	@Override
	public void analyze(List<Hotspot> hotspots) throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		// TODO Auto-generated method stub
		Pair<CallGraph, PointerAnalysis> p = buildCG();
		CallGraph cg = p.fst();
		PointerAnalysis pa = p.snd();
		WalaCGVisualizer vis = new WalaCGVisualizer();
		vis.visualize(cg, "cfg_test.dot");
		vis.printLabel("label.txt");
		Set<IBox> boxSet = findHotspots(cg, hotspots);
		IBox[] boxes = boxSet.toArray(new IBox[0]);
		for(IBox box : boxes){
			System.err.println("Spot: " + box);
		}
		System.err.println("Field Def analysis...");
		FieldDefAnalysis fda = new FieldDefAnalysis(cg, pa);
		System.err.println("Build Constraint Graph...");
		
		IBox[] targets = new IBox[]{boxes[0]};
//		Box[] targets = boxes;
		ConstraintGraph graph = buildConstraintGraph(cg, fda, targets);
		System.err.println("Print Constraint Graph...");
		ConstraintGraphVisualizer cgvis = new ConstraintGraphVisualizer();
		cgvis.visualize(graph, "const0.dot", targets);
		graph.optimize();
		ConstraintGraphVisualizer cgvis2 = new ConstraintGraphVisualizer();
		cgvis.visualize(graph, "const_op0.dot", targets);
		
		System.out.println("--- String modeling warning ---");
		for(String warning : StringModel.getWarnings()){
			System.out.println("[Warning] " + warning);
		}
		System.out.println("------------");
	}
	
	private Pair<CallGraph, PointerAnalysis> buildCG() throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException{
		IClassHierarchy cha = ClassHierarchy.make(scope);
		AnalysisOptions options = new AnalysisOptions();
		IRFactory<IMethod> irFactory = new DexIRFactory();
		AnalysisCache cache = new AnalysisCache(irFactory);
		options.setReflectionOptions(ReflectionOptions.NONE);
		options.setAnalysisScope(scope);
		options.setEntrypoints(getEntrypoints(cha, scope, options, cache));
		options.setSelector(new ClassHierarchyClassTargetSelector(cha));
		options.setSelector(new ClassHierarchyMethodTargetSelector(cha));
//		CallGraphBuilder cgb = new nCFABuilder(0, cha, options, cache, null, null);
		CallGraphBuilder cgb = ZeroXCFABuilder.make(cha, options, cache, null, null, 0);
		return Pair.make(cgb.makeCallGraph(options, null), cgb.getPointerAnalysis());
	}
	
	private Iterable<Entrypoint> getEntrypoints(final IClassHierarchy cha, AnalysisScope scope, AnalysisOptions option, AnalysisCache cache){
		Iterable<Entrypoint> entrypoints = null;
		
		if(cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, "Lgeneratedharness/GeneratedAndroidHarness")) == null){
			Set<LocatorFlags> flags = HashSetFactory.make();
			flags.add(LocatorFlags.INCLUDE_CALLBACKS);
			flags.add(LocatorFlags.EP_HEURISTIC);
			flags.add(LocatorFlags.CB_HEURISTIC);
			AndroidEntryPointLocator eps = new AndroidEntryPointLocator(flags);
			List<AndroidEntryPoint> es = eps.getEntryPoints(cha);
					
			final List<Entrypoint> entries = new ArrayList<Entrypoint>();
			for (AndroidEntryPoint e : es) {
				entries.add(e);
			}
	
			entrypoints = new Iterable<Entrypoint>() {
				@Override
				public Iterator<Entrypoint> iterator() {
					return entries.iterator();
				}
			};
		}else{
			IClass root = cha.lookupClass(TypeReference.find(ClassLoaderReference.Primordial, "Lgeneratedharness/GeneratedAndroidHarness"));
			IMethod rootMethod = root.getMethod(new Selector(Atom.findOrCreateAsciiAtom("androidMain"), Descriptor.findOrCreate(null, TypeName.findOrCreate("V"))));
			Entrypoint droidelEntryPoint = new DefaultEntrypoint(rootMethod, cha);
			
			final List<Entrypoint> entry = new ArrayList<Entrypoint>();
			entry.add(droidelEntryPoint);
			
			entrypoints = new Iterable<Entrypoint>(){
				@Override
				public Iterator<Entrypoint> iterator(){
					return entry.iterator();
				}
			};
		}
		return entrypoints;
	}
	
	private Set<IBox> findHotspots(CallGraph cg, List<Hotspot> hotspots){
		Set<IBox> boxes = new HashSet<IBox>();
		for(CGNode node : cg){
			IR ir = node.getIR();
			
			if(ir == null)
				continue;
			
			SSAInstruction[] insts = ir.getInstructions();
			for(int i=0; i<insts.length; i++){
				SSAInstruction inst = insts[i];
				
				if(inst == null)
					continue;
				
				for(Hotspot hotspot : hotspots){
					if(isHotspot(inst, hotspot)){
						int use = inst.getUse(hotspot.index() + 1);
						boxes.add(new VarBox(node, i, use));
					}
				}
			}
		}
		return boxes;
	}
	
	private boolean isHotspot(SSAInstruction inst, Hotspot hotspot){
		if(hotspot instanceof ArgumentHotspot){
			ArgumentHotspot argHotspot = (ArgumentHotspot) hotspot;
			if(inst instanceof SSAAbstractInvokeInstruction){
				SSAAbstractInvokeInstruction invokeInst = (SSAAbstractInvokeInstruction) inst;
				MethodReference targetMr = invokeInst.getDeclaredTarget();
				if(targetMr.getName().toString().equals(argHotspot.getMethodName()) && targetMr.getNumberOfParameters() == argHotspot.getParamNum())
					return true;
			}
		}
		return false;
	}
	
	private ConstraintGraph buildConstraintGraph(CallGraph cg, FieldDefAnalysis fda, IBox... initials){
		for(IBox box : initials){
			System.err.println("TargetSpot: " + box);
		}
		
		ConstraintGraph graph = new ConstraintGraph();
		ConstraintVisitor v = new ConstraintVisitor(cg, fda, graph, new InteractionConstraintMonitor(cg, InteractionConstraintMonitor.CLASSTYPE_ALL, InteractionConstraintMonitor.NODETYPE_NONE));
		for(IBox initial : initials)
			worklist.add(initial);
		
		int iter = 1;
		while(!worklist.isEmpty()){
			IBox box = worklist.pop();
			System.out.println("#Iter(" + (iter++) + ", size: " + worklist.size() + ") " + box);
			Set<IBox> res = box.visit(v);
			
			for(IBox next : res)
				worklist.add(next);
		}
		System.out.println("--- constraint visitor warning ---");
		for(String str : v.getWarnings()){
			System.out.println("[Warning] " + str);
		}
		System.out.println("----------");
		
		return graph;
	}

	class WorkList{
		private List<IBox> list;
		private Set<IBox> visited;
		public WorkList(){
			list = new ArrayList<IBox>();
			visited = new HashSet<IBox>();
		}
		
		public void add(IBox box){
			if(!visited.contains(box)){
				list.add(box);
				visited.add(box);
			}
		}
		
		public IBox pop(){
			IBox box = list.get(0);
			list.remove(0);
			return box;
		}
		
		public boolean isEmpty(){
			return list.isEmpty();
		}
		
		public int size(){
			return list.size();
		}
	}
}