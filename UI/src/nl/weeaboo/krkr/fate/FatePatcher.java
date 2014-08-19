package nl.weeaboo.krkr.fate;

import java.util.HashMap;
import java.util.Map;

import nl.weeaboo.krkr.fate.FateScriptConverter.Language;
import nl.weeaboo.vnds.Patcher;

public class FatePatcher extends Patcher {
	
	private Language lang;
	
	public FatePatcher(Language lang) {
		this.lang = lang;
	}
	
	//Functions
	public void fillAppendMap(Map<String, String> appendMap) {
		String temp;

		//Prologue
		{
			appendMap.put("prologue00.ks", "jump prologue01.scr"); 
			appendMap.put("prologue01.ks", "jump prologue02.scr"); 
			appendMap.put("prologue02.ks", "text <End of prologue>\njump main.scr"); 
		}
		
		//Fate
		{
			//Subroutines aren't supported, so I have to change around the append-data a bit
			temp = appendMap.remove("fate15-17.ks");
			appendMap.put("fate-ending.ks", (temp != null ? temp : "") + "\njump fate15-17.scr post");
		}
		
		//UBW
		{
			//Subroutines aren't supported, so I have to change around the append-data a bit
			temp = appendMap.remove("ubw14-15.ks");
			appendMap.put("ubw-ending.ks", (temp != null ? temp : "") + "\njump ubw14-15.scr post");
	
			temp = appendMap.remove("ubw14-16.ks");
			appendMap.put("ubw-ending2.ks", (temp != null ? temp : "") + "\njump ubw14-16.scr post");
		}

		//HF
		{
			//Subroutines aren't supported, so I have to change around the append-data a bit
			temp = appendMap.remove("hf16-09.ks");
			appendMap.put("hf-ending2.ks", (temp != null ? temp : "") + "\njump hf16-09.scr post");
			
			temp = appendMap.remove("hf16-13.ks");
			appendMap.put("hf-ending.ks", (temp != null ? temp : "") + "\njump hf16-13.scr post");			
		}
	}
	
	public void patchPre(Map<String, Map<Integer, String>> patch) {
		//Shared
		
		//Language Dependent
		if (lang == Language.EN) {
			patchPreEN(patch);
		} else if (lang == Language.JA) {
			patchPreJA(patch);
		} else if (lang == Language.CH) {
			patchPreCH(patch);
		}
	}
	public void patchPreEN(Map<String, Map<Integer, String>> patch) {
		//Warning, pre-patching may be broken
	}
	public void patchPreJA(Map<String, Map<Integer, String>> patch) {
		//Warning, pre-patching may be broken
	}
	public void patchPreCH(Map<String, Map<Integer, String>> patch) {
		//Warning, pre-patching may be broken
	}
	
	public void patchPost(Map<String, Map<Integer, String>> patch) {
		Map<Integer, String> map = null;

		{
			//Prologue
			map = patchPost(patch, "prologue-00.ks"); 
			map.put(5, "#type moon logo removed");
	
			//Fate
			map = patchPost(patch, "fate01-00.ks");
			map.put(6,  RM_TEXT);
			map.put(8,  "bgload special/fate/bone1.jpg\ntext I am the bone of my sword.");
			map.put(9,  "bgload special/fate/bone2.jpg\ntext Steel is my body, and fire is my blood.");
			map.put(10, "bgload special/fate/bone3.jpg\ntext I have created over a thousand blades.");
			map.put(20, RM_TEXT);
			map.put(21, "bgload special/fate/bone4.jpg\ntext Unknown to Death.");
			map.put(22, "bgload special/fate/bone5.jpg\ntext Nor known to Life.");
			map.put(23, "bgload special/fate/bone6.jpg\ntext Have withstood pain to create many weapons.");
			map.put(24, "bgload special/fate/bone7.jpg\ntext Yet, those hands will never hold anything.");
			map.put(25, "bgload special/fate/bone8.jpg\ntext So as I pray, unlimited blade works.");
			map.put(40, RM_TEXT);
			map.put(43, RM_TEXT);
			map.put(44, RM_TEXT);
	
			//UBW
			map = patchPost(patch, "ubw14-15.ks");		
			map.put(5, "jump ubw-ending.scr\nlabel post");
			
			map = patchPost(patch, "ubw14-16.ks");		
			map.put(5, "jump ubw-ending2.scr\nlabel post");
			
			//HF
			
		}				
		
		//Language Dependent
		if (lang == Language.EN) {
			patchPostEN(patch);
		} else if (lang == Language.JA) {
			patchPostJA(patch);
		} else if (lang == Language.CH) {
			patchPostCH(patch);
		}
	}
	public void patchPostEN(Map<String, Map<Integer, String>> patch) {
		Map<Integer, String> map = null;

		//Prologue

		//Fate
		map = patchPost(patch, "fate04-05.ks"); 
		map.put(757, "#status screen related");
		map = patchPost(patch, "fate04-18.ks"); 
		map.put(111, "#status screen related");
		map = patchPost(patch, "fate05-11.ks"); 
		map.put(96, "#status screen related");
		
		map = patchPost(patch, "fate15-17.ks");		
		map.put(319, "jump fate-ending.scr\nlabel post");
		
		//UBW
		map = patchPost(patch, "ubw04-10.ks"); 
		map.put(507, "#status screen related");
		
		//HF (untranslated)
		map = patchPost(patch, "hf04-10.ks"); 
		map.put(886, "#status screen related");

		map = patchPost(patch, "hf16-09.ks");		
		map.put(307, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-12.ks");		
		map.put(305, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-13.ks");		
		map.put(395, "jump hf-ending.scr\nlabel post");
	}
	public void patchPostJA(Map<String, Map<Integer, String>> patch) {
		Map<Integer, String> map = null;
		
		//Prologue

		//Fate
		map = patchPost(patch, "fate04-05.ks"); 
		map.put(925, "#status screen related");
		map = patchPost(patch, "fate04-18.ks"); 
		map.put(131, "#status screen related");
		map = patchPost(patch, "fate05-11.ks"); 
		map.put(130, "#status screen related");
			
		map = patchPost(patch, "fate15-17.ks");		
		map.put(350, "jump fate-ending.scr\nlabel post");
		
		//UBW
		map = patchPost(patch, "ubw04-10.ks"); 
		map.put(628, "#status screen related");
		
		//HF
		map = patchPost(patch, "hf04-10.ks"); 
		map.put(886, "#status screen related");

		map = patchPost(patch, "hf16-09.ks");		
		map.put(307, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-12.ks");		
		map.put(305, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-13.ks");		
		map.put(395, "jump hf-ending.scr\nlabel post");
	}
	public void patchPostCH(Map<String, Map<Integer, String>> patch) {
		Map<Integer, String> map = null;
		
		//Prologue

		//Fate
		map = patchPost(patch, "fate04-05.ks"); 
		map.put(987, "#status screen related");
		map = patchPost(patch, "fate04-18.ks"); 
		map.put(138, "#status screen related");
		map = patchPost(patch, "fate05-11.ks"); 
		map.put(136, "#status screen related");
			
		map = patchPost(patch, "fate15-17.ks");		
		map.put(349, "jump fate-ending.scr\nlabel post");
		
		//UBW
		map = patchPost(patch, "ubw04-10.ks"); 
		map.put(683, "#status screen related");
		
		//HF
		map = patchPost(patch, "hf04-10.ks"); 
		map.put(1096, "#status screen related");

		map = patchPost(patch, "hf16-09.ks");		
		map.put(321, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-12.ks");		
		map.put(320, "jump hf-ending2.scr\nlabel post");

		map = patchPost(patch, "hf16-13.ks");		
		map.put(454, "jump hf-ending.scr\nlabel post");
	}
	
	protected Map<Integer, String> patchPost(Map<String, Map<Integer, String>> patchPost, String filename) {
		Map<Integer, String> map = new HashMap<Integer, String>();
		patchPost.put(filename, map);
		return map;
	}
	
	protected Map<Integer, String> patchPre(Map<String, Map<Integer, String>> patchPre, String filename) {
		Map<Integer, String> map = new HashMap<Integer, String>();
		patchPre.put(filename, map);
		return map;
	}
	
	//Getters
	
	//Setters
	
}
