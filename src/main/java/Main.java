/** takes a list of properties and for each of them determines and writes to a file the list of correlated properties and its frequency**/
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

public class Main
{
	static int x = 0;
	static final int RETRIES = 10;
	//	static Logger log = Logger.getLogger("bla");
	final static String[] ENDPOINTS = {"http://dbpedia.org/sparql","http://live.dbpedia.org/sparql"};
	static final int	THREADS	= 30;
	static int initialWaitMs = 1000;//in ms doubles each time  

	static File folder = new File("output");
	static {folder.mkdir();}

	static String getEndpoint()
	{
		if(x<0) x=0;
		return ENDPOINTS[x++%ENDPOINTS.length];
	}

	static String tsv(Object... x)
	{
		String s = Arrays.toString(x);
		return s.substring(1, s.length()-1).replace(',','\t');		
	}

	static class PropertyInfoGetter implements Callable<String>
	{
		final String query;
		final String property;
		final String label;
		final int nr;

	
		public PropertyInfoGetter(String query,String property,String label, int nr)
		{
			this.label=label;
			this.property=property;
			this.query=query;
			this.nr=nr;
		}

		private String doIt()
		{
			ResultSet rs = new QueryEngineHTTP(getEndpoint(), query).execSelect();
			StringBuilder sb = new StringBuilder();
			while(rs.hasNext())
			{
				QuerySolution qs = rs.next();
				String otherProperty = qs.getResource("?p").getURI();
				String otherLabel = qs.getLiteral("?l").getLexicalForm();
				int count = qs.getLiteral("?cnt").getInt();
				sb.append(tsv(property,label,otherProperty,otherLabel,count));
				/*if(rs.hasNext()) */{sb.append('\n');}
			}
			return sb.toString();
		}

		@Override public String call() throws Exception
		{	
			File file = Paths.get(folder.getPath(),property.substring(property.lastIndexOf('/')+1)).toFile();
			if(file.exists()) System.out.println(nr+ " exists, skipping property "+property);
			int waitMs = initialWaitMs;
			for(int retries=0;retries<=RETRIES;retries++)
			{
				try				
				{
					System.out.println(nr+"starting property "+property+" query "+query);
					String s = doIt();
					System.out.println(nr+"finished property "+property+" query "+query);
					try(PrintWriter out = new PrintWriter(file))
					{
						out.print(s);
					}
					return s;
				}
				catch(Exception e) 
				{
					e.printStackTrace();
					System.err.println(nr+"retry "+(retries+1)+"after wait of "+waitMs+"ms of query "+query);
					Thread.sleep(waitMs);
					waitMs=waitMs<<1;
				}			
			}
			System.err.println(nr+"no result after "+RETRIES+" retries for property "+property+" query "+query);
			return null;						
		}
	}

	public static void main(String[] args) throws FileNotFoundException, InterruptedException, ExecutionException
	{
		//		String getAllObjectProperties = "select distinct(?p) ?l ?domain ?range from <http://dbpedia.org> {?p a owl:ObjectProperty. ?p rdfs:label ?l. OPTIONAL ?p rdfs:domain ?domain. OPTIONAL ?p rdfs:range ?range. filter(langmatches(lang(?l),\"en\")). FILTER(STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\"))}";
		//		String getAllObjectProperties = "select distinct(?p) ?l from <http://dbpedia.org> {?p a owl:ObjectProperty. ?p rdfs:label ?l. filter(langmatches(lang(?l),\"en\")). FILTER(STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\"))}";
		Map<String,String> propertyLabels = new HashMap<>();
		try(Scanner in = new Scanner(new File("objectproperties.tsv")))
		{
			while(in.hasNextLine())
			{
				String[] tokens = in.nextLine().split("\t");
				propertyLabels.put(tokens[0],tokens[1]);
			}
		}
		System.out.println("processing "+propertyLabels.size()+" properties");
		//		ResultSet rsProperties = new QueryEngineHTTP(getEndpoint(), getAllObjectProperties).execSelect();
		//		List<String> queries = new LinkedList<String>();
		ExecutorService service = Executors.newFixedThreadPool(THREADS);
		//		List<Future<String>> futures = new LinkedList<>();
		Map<Future<String>,String> futures = new HashMap<>();
		try(PrintWriter out = new PrintWriter("properties.tsv"))
		{
			int nr=0;
			for(String property: propertyLabels.keySet())
				//			while(rsProperties.hasNext())
			{
				//				QuerySolution qsProperties = rsProperties.next();
				//				String property = qsProperties.getResource("?p").getURI();
				//				String label = qsProperties.getLiteral("?l").getLexicalForm();
				String label = propertyLabels.get(property);
				out.println(tsv(property,label));
				String query = "SELECT distinct(?p) ?l (COUNT(*) AS ?cnt) WHERE {?s <"+property+"> ?o. ?s ?p ?o. ?p rdfs:label ?l. filter(langmatches(lang(?l),\"en\")). FILTER(STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\")) } GROUP BY ?p ?l order by (?cnt)";		
				//			out.println(tsv(property,label,otherProperty,otherLabel,count));
				futures.put(service.submit(new PropertyInfoGetter(query, property, label,++nr)),query);
			}
		}

//		for(Future<String> future: futures.keySet())
//		{
//			String s = future.get();
//			if(s!=null)
//			{
//				System.out.print(s);
//				try(PrintWriter out = new PrintWriter(Paths.get(folder.getPath(),"propertypairs.tsv").toFile()))
//				{
//					out.print(s);
//				}
//			}
//		}
	}

}