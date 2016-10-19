
package gov.usgs.cida.coastalhazards.wps;

import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.utilities.properties.JNDISingleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.util.logging.Logging;

@DescribeProcess(title = "Unzip", description = "Unzips the specified file to GeoServer's file system, and returns the file path")
public class UnzipProcess {
    static final Logger LOGGER = Logging.getLogger(UnzipProcess.class);
    static final String TOKEN_PROPERTY_NAME = "gov.usgs.cida.coastalhazards.wps.unzip.process.token";
    static final String UNZIP_BASE_PROPERTY_NAME = "gov.usgs.cida.coastalhazards.wps.unzip.process.unzip.base";
    private DynamicReadOnlyProperties properties;
    private HttpClient httpClient;
    
    @DescribeResult(name = "filePath", description = "path to the directory where the file was unzipped")
    public List<String> execute(
            @DescribeParameter(name = "zipUrl", min = 1, max = 1, description = "URL to the zipped file to retrieve") String zipUrl,
            @DescribeParameter(name = "token", min = 1, max = 1, description = "Token for authorizing the upload") String token
            ){
        List<String> unzippedPaths = null;
        
        if(isAuthorized(token)){
            ZipInputStream zipStream = getZipFromUrl(zipUrl, getHttpClient());
            unzippedPaths = unzipToDir(zipStream, getNewZipDestination());
        } else {
            throw new ProcessException( new SecurityException("Not Authorized."));
        }
        return unzippedPaths;
    }
    boolean isAuthorized(String token){
        boolean authorized = false;
        String expectedToken = getProperties().getProperty(TOKEN_PROPERTY_NAME);
        if(null == expectedToken){
            throw new ProcessException(
                "Could not determine expected value for token. Please ensure that " + TOKEN_PROPERTY_NAME + " is defined on the server."
            );
        } else if(expectedToken.equals(token)){
            //overwrite the expected value right away
            expectedToken = null;
            authorized = true;
        }
        return authorized;
    }
    /**
     * Everything except for tests should call this method to get a new zip destination
     * @return a unique path
     */
    String getNewZipDestination(){
        return getNewZipDestination(getProperties().getProperty(UNZIP_BASE_PROPERTY_NAME));
    }
    
    /**
     * Only tests and the getNewZipDestionation() wrapper should call this directly
     * @param unzipBase
     * @return a unique path
     */
    String getNewZipDestination(String unzipBase){
        UUID uuid = UUID.randomUUID();
        if (null == unzipBase) {
            throw new ProcessException("Could not determine base directory in which to unzip. Please ensure that " + UNZIP_BASE_PROPERTY_NAME + " is defined on the server.");
        } else {
            String zipDir = unzipBase + File.separator + uuid.toString();
            return zipDir;
        }
    }
            
    ZipInputStream getZipFromUrl(String zipUrl, HttpClient client){
        HttpUriRequest req = new HttpGet(zipUrl);
        HttpResponse response;
        ZipInputStream zipStream = null;
        try {
            LOGGER.fine("retrieving zip from: " + zipUrl);
            response = client.execute(req);
            StatusLine status = response.getStatusLine();
            int statusCode = status.getStatusCode();
            if (400 <= statusCode) {
                throw new ProcessException("Could not retrieve file '" + zipUrl + "'. Got HTTP " + statusCode);
            } else {
                zipStream = new ZipInputStream(response.getEntity().getContent());
            }
        } catch (IOException ex) {
            throw new ProcessException(ex);
        }
        return zipStream;
    }
    
    List<String> unzipToDir(ZipInputStream zipStream, String zipDir) {
        List<String> unzippedFiles = new ArrayList<>();
        try {
            ZipEntry entry;
            while (null != (entry = zipStream.getNextEntry())) {
                if(!entry.isDirectory()){
                    String entryFileName = entry.getName();

                    File entryFile = new File(zipDir + File.separator + entryFileName);
                    String entryFileAbsolutePath = entryFile.getAbsolutePath();
                    LOGGER.fine("unzipping '" + entryFileName + "' to " + entryFileAbsolutePath);
                    new File(entryFile.getParent()).mkdirs();
                    FileOutputStream fos = null;
                    try{
                        fos = new FileOutputStream(entryFile);
                        IOUtils.copy(zipStream, fos);
                    } catch (FileNotFoundException ex) {
                        throw new ProcessException("Error finding file '" + entryFileAbsolutePath + "'.", ex);
                    } catch (IOException ex) {
                        throw new ProcessException("Error writing file '" + entryFileName + "' to '" + entryFileAbsolutePath+ "'.", ex);
                    } finally {
                        IOUtils.closeQuietly(fos);
                    }
                    unzippedFiles.add(entryFileAbsolutePath);
                }
            }
        } catch (IOException ex) {
            throw new ProcessException("error getting next entry in zip file" , ex);
        } finally {
            IOUtils.closeQuietly(zipStream);
        }
        return unzippedFiles;
    }
    /**
     * @return the properties, defaulting to JNDI
     */
    DynamicReadOnlyProperties getProperties() {
        return null == properties ? JNDISingleton.getInstance() : properties;
    }

    /**
     * @param properties the properties to set
     * Mostly used for testing
     */
    void setProperties(DynamicReadOnlyProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the httpClient
     */
    HttpClient getHttpClient() {
        return null == httpClient ? new DefaultHttpClient() : httpClient;
    }

    /**
     * @param httpClient the httpClient to set
     */
    void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
