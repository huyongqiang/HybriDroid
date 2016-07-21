package kr.ac.kaist.wala.hybridroid.test.annotation;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;

import kr.ac.kaist.hybridroid.analysis.HybridCFGAnalysis;
import kr.ac.kaist.wala.hybridroid.test.FileCollector;
import kr.ac.kaist.wala.hybridroid.test.HybriDroidTestRunner;

public class AnnotationTest {
	
	public static String TEST_DIR = "annotation";
	
	@Test
	public void missingAnnotationMethodShouldInvisible() throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException{
		File[] tests = FileCollector.getAPKsInDir(HybriDroidTestRunner.getTestDir() + File.separator + TEST_DIR);
		
		for(File f : tests){
			String testName = f.getName();
			HybridCFGAnalysis cfgAnalysis = new HybridCFGAnalysis();
			cfgAnalysis.main(f.getCanonicalPath(), HybriDroidTestRunner.getLibPath());
			for(String s: cfgAnalysis.getWarnings()){
				switch(testName){
				case "AnnotationTest.apk":
					assertEquals(testName + ": ", "[Error] the [[JAVA_JS_BRIDGE:<Application,Lkr/ac/kaist/wala/hybridroid/test/annotation/JSBridge>],<field getLastName>] is not matched.", s);
					break;
				}
			}
		}
	}
	
	@Test
	public void ignoreAnnotationInfoBeforeKitkat(){
		//TODO: implement test apps
	}
	
	
}