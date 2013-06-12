import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

/** Use alldifferent.tsv created by FromLemon and add correlated resource labels to it. Because only a small amount of them are properties (which already exist),
 * we also need to get correlated classes, where we need to find out how (because just select ?c {?s a ?c. ?s a &lt;x&gt;.} might be too simple.**/
public class LemonDifferentCorrelated
{
	public static Set<String> resultSetToList(ResultSet rs)
	{
		Set<String> list = new HashSet<String>();
		while(rs.hasNext())
		{
			QuerySolution qs = rs.nextSolution();
			try {
				list.add(URLDecoder.decode(qs.get(qs.varNames().next()).toString(), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				list.add(qs.get(qs.varNames().next()).toString());
				e.printStackTrace();
			}
		}
		return list;
	}
	public static Set<String> superClassesOf(String clazz) {return superClassesOf(new HashSet<String>(Collections.singletonList(clazz)));}
	public static Set<String> superClassesOf(Set<String> classes)
	{
		Set<String> superClasses = new HashSet<String>();    	
		for(String clazz: classes)
		{
			String query = "SELECT distinct(?class) from <"+Defaults.graph+"> {<"+clazz+"> rdfs:subClassOf ?class.}";
			superClasses.addAll(resultSetToList(new QueryEngineHTTP(Defaults.endpoint, query).execSelect()));
		}      
		if(!superClasses.contains("http://www.w3.org/2002/07/owl#Thing")&&superClasses.size()>classes.size())
		{
			superClasses.addAll(superClassesOf(superClasses));
		}
		return superClasses;
	}

	public static Map<String,Integer> getCorrelatedClassLabels(String clazz)
	{
		if(!clazz.startsWith("http://")) clazz=Defaults.DBO+clazz;
		Set<String> superClasses = superClassesOf(clazz);
		Map<String,Integer> clazzes = new HashMap<String,Integer>();
		// no yago, because it's class hierarchy is too flat, we would get too many useless low frequency results 
		String query = "select distinct(?c), ?l, count(?s as ?cnt) from <"+Defaults.graph+"> {?s a <"+clazz+">. ?s a ?c.  FILTER(!STRSTARTS(STR(?c), \"http://dbpedia.org/class/yago/\")). ?c rdfs:label ?l. FILTER(langmatches(lang(?l),\"en\")).} order by desc(count(?s))";
//		System.out.println(query);
		ResultSet rs = new QueryEngineHTTP(Defaults.endpoint, query).execSelect();
//		int maxFrequency = 0;
		while(rs.hasNext())
		{
			QuerySolution qs = rs.next();
			String correlatedClazz = qs.get("?c").asResource().getURI();
			String correlatedClassLabel = qs.get("?l").asLiteral().getLexicalForm();
			if(superClasses.contains(correlatedClazz)) {continue;}
//			for(Iterator<String> it = qs.varNames();it.hasNext();) System.out.println(it.next());
			// bug? cnt gets returned as "callret-1
			int frequency = qs.get("?callret-2").asLiteral().getInt();
//			if(clazz.equals(correlatedClazz))
//			{
////				maxFrequency=frequency; // max occurrences with itself
//				continue;
//			}
			clazzes.put(correlatedClassLabel, frequency);
		}

		return clazzes;
	}

	public static void main(String[] args) throws FileNotFoundException
	{
		Map<String,Map<String,Integer>> correlatedPropertyLabelFrequencies = new HashMap<>();

		try(Scanner in = new Scanner(new File("output/correlatedproperties/combined/correlatedproperties.tsv")))
		{
			while(in.hasNextLine())
			{
				String line = in.nextLine();
				if(line.isEmpty()) {continue;}
				String[] tokens = line.split("\t");
				String property = tokens[0];
				int frequency = Integer.valueOf(tokens[4]); 
				String correlatedLabel = tokens[3];
				Map<String,Integer> labelFrequency = correlatedPropertyLabelFrequencies.get(property);
				if(labelFrequency==null) {correlatedPropertyLabelFrequencies.put(property,labelFrequency = new HashMap<String,Integer>());}
				labelFrequency.put(correlatedLabel, frequency);
			}
		}
//		System.out.println(correlatedPropertyLabelFrequencies);
		//		Map<String,Map<String,Integer>> correlatedClassFrequencies = new HashMap<>();

		File folder = new File("output/correlatedLemon");
		folder.mkdirs();
		File file = Paths.get(folder.getPath(),"correlatedlemon.tsv").toFile();
		try(PrintWriter out = new PrintWriter(file))
		{
		try(Scanner in = new Scanner(Lemon.allDifferent))
		{
			int i=0;
			while(in.hasNextLine())
			{
				
				String line = in.nextLine();
				if(line.isEmpty()) {continue;}
//				System.out.println(line);
				String[] tokens = line.split("\t");
				String keyword = tokens[0].trim();
				String resource = tokens[1].trim();
//				System.out.println(resource);
				Map<String,Integer> labelFrequencies;
				if(Character.isLowerCase(resource.charAt(0)))
				{
					// it's a property
					labelFrequencies = correlatedPropertyLabelFrequencies.get(resource);
				}
				else if(Character.isUpperCase(resource.charAt(0)))
				{
					labelFrequencies = getCorrelatedClassLabels(resource);				
				}
				else {throw new AssertionError(resource);}
				if(labelFrequencies==null||labelFrequencies.isEmpty()) {continue;}
				String s = CorrelatedProperties.tsv(keyword,resource,labelFrequencies.toString().replaceAll("[\\{\\}]", ""));
				System.out.println(++i+": "+s);
				out.println(s);
			}
		}
		}
	}

}