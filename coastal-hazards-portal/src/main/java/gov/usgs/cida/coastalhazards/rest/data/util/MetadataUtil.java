package gov.usgs.cida.coastalhazards.rest.data.util;

import gov.usgs.cida.coastalhazards.model.Bbox;
import gov.usgs.cida.coastalhazards.model.Service;
import gov.usgs.cida.coastalhazards.rest.data.MetadataResource;
import gov.usgs.cida.coastalhazards.xml.model.Bounding;
import gov.usgs.cida.coastalhazards.xml.model.Horizsys;
import gov.usgs.cida.coastalhazards.xml.model.Idinfo;
import gov.usgs.cida.coastalhazards.xml.model.Metadata;
import gov.usgs.cida.coastalhazards.xml.model.Spdom;
import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.utilities.properties.JNDISingleton;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author isuftin
 */
public class MetadataUtil {
	
	private static final Logger log = LoggerFactory.getLogger(MetadataUtil.class);

	private static final String cswLocalEndpoint;
	private static final String cswExternalEndpoint;
	private static final String cchn52Endpoint;
	private static final DynamicReadOnlyProperties props;
	private static final String NAMESPACE_CSW = "http://www.opengis.net/cat/csw/2.0.2";
	private static final String NAMESPACE_DC = "http://purl.org/dc/elements/1.1/";
	
	public static final String[] XML_PROLOG_PATTERNS = {"<\\?xml[^>]*>", "<!DOCTYPE[^>]*>"};

	static {
		props = JNDISingleton.getInstance();
		cswLocalEndpoint = props.getProperty("coastal-hazards.csw.internal.endpoint");
		cswExternalEndpoint = props.getProperty("coastal-hazards.csw.endpoint");
		cchn52Endpoint = props.getProperty("coastal-hazards.n52.endpoint");
	}

	public static String doCSWInsertFromUploadId(String metadataId) throws IOException, ParserConfigurationException, SAXException {
		String insertedId = null;

		MetadataResource metadata = new MetadataResource();
		Response response = metadata.getFileById(metadataId);
		String xmlWithoutHeader = stripXMLProlog(response.getEntity().toString());
		insertedId = doCSWInsert(xmlWithoutHeader);

		return insertedId;
	}
	
	public static String doCSWInsertFromString(String metadata) throws IOException, ParserConfigurationException, SAXException {
		String insertedId = null;
		
		String xmlWithoutHeader = stripXMLProlog(metadata);
		insertedId = doCSWInsert(xmlWithoutHeader);
		
		return insertedId;
	}
	
	public static String stripXMLProlog(String xml) {
		String xmlWithoutHeader = xml;
		for (String prolog : XML_PROLOG_PATTERNS) {
			xmlWithoutHeader = xmlWithoutHeader.replaceAll(prolog, "");
		}
		return xmlWithoutHeader;
	}

	private static String doCSWInsert(String xmlWithoutHeader) throws IOException, ParserConfigurationException, SAXException {
		String id = null;
		String cswRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<csw:Transaction service=\"CSW\" version=\"2.0.2\" xmlns:csw=\"http://www.opengis.net/cat/csw/2.0.2\">"
				+ "<csw:Insert>"
				+ xmlWithoutHeader
				+ "</csw:Insert>"
				+ "</csw:Transaction>";
		HttpUriRequest req = new HttpPost(cswLocalEndpoint);
		HttpClient client = new DefaultHttpClient();
		req.addHeader("Content-Type", "text/xml");
		if (!StringUtils.isBlank(cswRequest) && req instanceof HttpEntityEnclosingRequestBase) {
			StringEntity contentEntity = new StringEntity(cswRequest);
			((HttpEntityEnclosingRequestBase) req).setEntity(contentEntity);
		}
		HttpResponse resp = client.execute(req);
		StatusLine statusLine = resp.getStatusLine();

		if (statusLine.getStatusCode() != 200) {
			throw new IOException("Error in response from csw");
		}
		String data = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		if (data.contains("ExceptionReport")) {
			log.error(data);
			throw new IOException("Error in response from csw");
		}
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(data.getBytes()));
		JXPathContext ctx = JXPathContext.newContext(doc.getDocumentElement());
		ctx.registerNamespace("csw", NAMESPACE_CSW);
		ctx.registerNamespace("dc", NAMESPACE_DC);
		Node inserted = (Node) ctx.selectSingleNode("//csw:totalInserted/text()");
		if (1 == Integer.parseInt(inserted.getTextContent())) {
			Node idNode = (Node) ctx.selectSingleNode("//dc:identifier/text()");
			id = idNode.getTextContent();
		}
		return id;
	}

	/**
	 * I really don't like this in its current form, we should rethink this
	 * process and move this around
	 *
	 * @param metadataEndpoint metadata endpoint for retreival
	 * @param attr attribute summary is for
	 * @return
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	static public String getSummaryFromWPS(String metadataEndpoint, String attr) throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
		String wpsRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<wps:Execute xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" service=\"WPS\" version=\"1.0.0\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd\">"
				+ "<ows:Identifier>org.n52.wps.server.r.item.summary</ows:Identifier>"
				+ "<wps:DataInputs>"
				+ "<wps:Input>"
				+ "<ows:Identifier>input</ows:Identifier>"
				+ "<wps:Data>"
				+ "<wps:LiteralData><![CDATA["
				+ metadataEndpoint
				+ "]]></wps:LiteralData>"
				+ "</wps:Data>"
				+ "</wps:Input>"
				+ "<wps:Input>"
				+ "<ows:Identifier>attr</ows:Identifier>"
				+ "<wps:Data>"
				+ "<wps:LiteralData>" + attr + "</wps:LiteralData>"
				+ "</wps:Data>"
				+ "</wps:Input>"
				+ "</wps:DataInputs>"
				+ "<wps:ResponseForm>"
				+ "<wps:RawDataOutput>"
				+ "<ows:Identifier>output</ows:Identifier>"
				+ "</wps:RawDataOutput>"
				+ "</wps:ResponseForm>"
				+ "</wps:Execute>";
		HttpUriRequest req = new HttpPost(cchn52Endpoint + "/WebProcessingService");
		HttpClient client = new DefaultHttpClient();
		req.addHeader("Content-Type", "text/xml");
		if (!StringUtils.isBlank(wpsRequest) && req instanceof HttpEntityEnclosingRequestBase) {
			StringEntity contentEntity = new StringEntity(wpsRequest);
			((HttpEntityEnclosingRequestBase) req).setEntity(contentEntity);
		}
		HttpResponse resp = client.execute(req);
		StatusLine statusLine = resp.getStatusLine();

		if (statusLine.getStatusCode() != 200) {
			throw new IOException("Error in response from wps");
		}
		String data = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		if (data.contains("ExceptionReport")) {
			String error = "Error in response from wps";
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(data.getBytes()));
			JXPathContext ctx = JXPathContext.newContext(doc.getDocumentElement());
			ctx.registerNamespace("ows", "http://www.opengis.net/ows/1.1");
			List<Node> nodes = ctx.selectNodes("ows:Exception/ows:ExceptionText/text()");
			if (nodes != null && !nodes.isEmpty()) {
				StringBuilder builder = new StringBuilder();
				for (Node node : nodes) {
					builder.append(node.getTextContent()).append(System.lineSeparator());
				}
				error = builder.toString();
			}
			throw new RuntimeException(error);
		}
		return data;
	}

	public static String extractMetadataFromShp(InputStream is) {
		String metadata = null;
		ZipInputStream zip = new ZipInputStream(is);
		
		try {
			ZipEntry entry = null;
			while (null != (entry = zip.getNextEntry())) {
				String name = entry.getName();
				if (name.endsWith(".xml")) {
					BufferedReader buf = new BufferedReader(new InputStreamReader(zip));
					StringWriter writer = new StringWriter();
					String line = null;
					while (null != (line = buf.readLine())) {
						writer.write(line);
					}
					metadata = writer.toString();
					zip.closeEntry();
				} else {
					zip.closeEntry();
				}
			}
		} catch (IOException e) {
			log.error("Error with shapefile", e);
		} finally {
			IOUtils.closeQuietly(zip);
		}
		return metadata;
	}
	
	public static String getMetadataByIdUrl(String id) {
		return cswExternalEndpoint + "?service=CSW&request=GetRecordById&version=2.0.2&typeNames=fgdc:metadata&id=" + id +"&outputSchema=http://www.opengis.net/cat/csw/csdgm&elementSetName=full";
	}
	
	public static Service makeCSWServiceForUrl(String url) {
		Service csw = new Service();
		csw.setType(Service.ServiceType.csw);
		csw.setEndpoint(url);
		return csw;
	}
        
        public static Bbox getBoundingBoxFromFgdcMetadata(String inMetadata) throws JAXBException, UnsupportedEncodingException{
            
                Bbox bbox = new Bbox();
                //parse out the WGS84 bbox from the metadata xml
                Metadata metadata = null;

                // JAXB will require jaxb-api.jar and jaxb-impl.jar part of java 1.6. Much safer way to interrogate xml and maintain than regex
                try {
                        JAXBContext jaxbContext = JAXBContext.newInstance(Metadata.class);

                        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                        metadata = (Metadata) jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(inMetadata.getBytes("UTF-8")));               

                }     catch (JAXBException e) { //schema used https: www.fgdc.gov/schemas/metadata/fgdc-std-001-1998-sect01.xsd
                            log.error("Unable to parse xml file. Has the schema changed? https:www.fgdc.gov/schemas/metadata/fgdc-std-001-1998-sect01.xsd :" + e.getMessage());
                            throw e;
                }  
         
                Idinfo idinfo = metadata.getIdinfo();
                Spdom spdom = idinfo.getSpdom();
                Bounding bounding = spdom.getBounding();
        
                double minx = bounding.getWestbc();
                double miny = bounding.getSouthbc();
                double maxx = bounding.getEastbc();
                double maxy = bounding.getNorthbc();
        
                Bbox result = new Bbox();
                result.setBbox(minx, miny, maxx, maxy);

                bbox.setBbox(minx, miny, maxx, maxy);
            
                return bbox;
        }
        
        public static CoordinateReferenceSystem getCrsFromFgdcMetadata(String inMetadata) throws FactoryException, JAXBException, UnsupportedEncodingException{
           //create the WKT to instantiate a CRS object from org.geotools.referencing
                final String lineSep = System.getProperty("line.separator", "\n");
           
                Metadata metadata = null;
                try {
                        JAXBContext jaxbContext = JAXBContext.newInstance(Metadata.class);

                        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                        metadata = (Metadata) jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(inMetadata.getBytes("UTF-8")));               

                }     catch (JAXBException e) { //schema used https: www.fgdc.gov/schemas/metadata/fgdc-std-001-1998-sect01.xsd
                            log.error("Unable to parse xml file. Has the schema changed? https:www.fgdc.gov/schemas/metadata/fgdc-std-001-1998-sect01.xsd :" + e.getMessage());
                            throw e;
                }  
                
                Horizsys horizsys = metadata.getSpref().getHorizsys();
                
                String ellips = horizsys.getGeodetic().getEllips();
                String horizdn = horizsys.getGeodetic().getHorizdn();
                double denflat = horizsys.getGeodetic().getDenflat();
                double semiaxis = horizsys.getGeodetic().getSemiaxis();
                
                String mapprojn = horizsys.getPlanar().getMapproj().getMapprojn();
                double feast = horizsys.getPlanar().getMapproj().getMapprojp().getFeast();
                double fnorth = horizsys.getPlanar().getMapproj().getMapprojp().getFnorth();
                double latprjo = horizsys.getPlanar().getMapproj().getMapprojp().getLatprjo();
                double longcm = horizsys.getPlanar().getMapproj().getMapprojp().getLongcm();
                double stdparll = horizsys.getPlanar().getMapproj().getMapprojp().getStdparll();
                
        /*      PROJCS["USA_Contiguous_Albers_Equal_Area_Conic_USGS_version", //mapprojn
                GEOGCS["GCS_North_American_1983",
                DATUM["D_North_American_1983", //horizdn
                SPHEROID["GRS_1980",6378137.0,298.257222101]],
                PRIMEM["Greenwich",0.0],
                UNIT["Degree",0.0174532925199433]],
                PROJECTION["Albers"],
                PARAMETER["False_Easting",0.0],
                PARAMETER["False_Northing",0.0],
                PARAMETER["Central_Meridian",-96.0],
                PARAMETER["Standard_Parallel_1",29.5],
                PARAMETER["Standard_Parallel_2",45.5],
                PARAMETER["Latitude_Of_Origin",23.0],
                UNIT["Meter",1.0]]  */
                
                // these defaults were derived from the first 3 raster files meta-data CR, AE, PAE
                // Hoping that these can be optional or located in future metadata in which case 
                // an if check should be performed and the value replaced if it doesn't match the default
                String defaultGcs = "GCS_North_American_1983";
                String defaultPrimeM = "Greenwich\",0.0]";
                String defaultUnit = "Degree\",0.0174532925199433]]";
                String defaultProjection = "Albers";
                String defaultLengthUnit = "Meter";
                double defaultLengthValue = 1.0;
                
                StringBuilder builder = new StringBuilder(500);
                
                builder.append("PROJCS[");
                builder.append("\"");  // quote
                builder.append(mapprojn);
                builder.append("\"");  // quote
                builder.append(",");   // comma
                builder.append(lineSep);
                
                builder.append("GEOGCS[");
                builder.append("\"");  // quote
                builder.append(defaultGcs);  // replace if the Gcs is found in the meta-data
                builder.append("\"");  // quote
                builder.append(",");   // comma
                builder.append(lineSep);
                
                builder.append("DATUM[");
                builder.append("\"");  // quote
                builder.append(horizdn);
                builder.append("\"");  // quote
                builder.append(",");   // comma
                builder.append(lineSep);                
                               
                builder.append("SPHEROID[");
                builder.append("\"");  // quote
                builder.append(ellips);
                builder.append("\"");  // quote
                builder.append(",");   // comma                
                builder.append(semiaxis);
                builder.append(",");   // comma
                builder.append(denflat);
                builder.append("]]");
                builder.append(",");   // comma
                builder.append(lineSep);
                
                builder.append("PRIMEM[");
                builder.append("\"");  // quote
                builder.append(defaultPrimeM);
                builder.append(",");
                builder.append(lineSep);
                
                builder.append("UNIT[");
                builder.append("\"");  // quote
                builder.append(defaultUnit);  //get pa
                builder.append(",");
                builder.append(lineSep);
             
                builder.append("PROJECTION[");
                builder.append("\"");  // quote
                builder.append(defaultProjection);
                builder.append("\"]");  // quote
                builder.append(",");
                builder.append(lineSep);
                
                builder.append(getParameterNode("False_Easting",feast));
                builder.append(",");
                builder.append(lineSep);
                
                builder.append(getParameterNode("False_Northing",fnorth));
                builder.append(",");
                builder.append(lineSep);

                builder.append(getParameterNode("Central_Meridian",longcm));
                builder.append(",");
                builder.append(lineSep);                
                
                builder.append(getParameterNode("Standard_Parallel_1",29.5)); //#TODO# relace with value
                builder.append(",");
                builder.append(lineSep);
                
                builder.append(getParameterNode("Standard_Parallel_2",stdparll));
                builder.append(",");
                builder.append(lineSep);

                builder.append(getParameterNode("Latitude_Of_Origin",latprjo));
                builder.append(","); 
                builder.append(lineSep);
                
                builder.append("UNIT[");
                builder.append("\"");  // quote
                builder.append(defaultLengthUnit); //Meter
                builder.append("\"");  // quote
                builder.append(",");
                builder.append(defaultLengthValue);
                builder.append("]]");

                String wkt = builder.toString();

           CoordinateReferenceSystem crs = CRS.parseWKT(wkt); // same as FactoryFinder.getCRSFactory(null).createFromWKT(wkt);
            
          //  CoordinateReferenceSystem crs = new DefaultGeocentricCRS(props, datum, cs);
            
            //look for the following block in the metadata to parse out the CRS:

            //May want to pass the key parts to a new method in here:
            //https://github.com/USGS-CIDA/coastal-hazards/blob/e5ccc15780f1dfb1f53edc97190ec79c9c501b13/coastal-hazards-wps/src/main/java/gov/usgs/cida/coastalhazards/util/CRSUtils.java
            return crs;
        }
        
        private static String getParameterNode(String name, double value){
            //exp PARAMETER["False_Easting",0.0]
            StringBuilder sb = new StringBuilder(50);
            
            sb.append("PARAMETER[");
            sb.append("\"");  // quote
            sb.append(name);
            sb.append("\"");  // quote
            sb.append(",");   // comma
            sb.append(value);
            sb.append("]");
            
            return sb.toString();
        }
}
