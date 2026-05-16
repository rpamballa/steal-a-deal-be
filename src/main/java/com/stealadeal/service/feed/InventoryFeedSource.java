package com.stealadeal.service.feed;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI for retrieving raw inventory feed content. The default
 * {@link DefaultInventoryFeedSource} supports http(s), file, and
 * classpath locations — enough for HTTP/SFTP-exported syndication
 * feeds (vAuto, HomeNet, DealerCenter) and local testing. DMS-specific
 * pull adapters (CDK, Reynolds) drop in behind the same interface.
 */
public interface InventoryFeedSource {

    String name();

    boolean supports(String location);

    InputStream open(String location) throws IOException;
}
