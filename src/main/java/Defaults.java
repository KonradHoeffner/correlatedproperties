import java.util.Arrays;

public class Defaults
{
	public final static String DBO = "http://dbpedia.org/ontology/";
	public final static String endpoint = "http://dbpedia.org/sparql"; 
	public final static String graph = "http://dbpedia.org";
	
	static String tsv(Object... x)
	{
		String s = Arrays.toString(x);
		return s.substring(1, s.length()-1).replace(',','\t');		
	}
}