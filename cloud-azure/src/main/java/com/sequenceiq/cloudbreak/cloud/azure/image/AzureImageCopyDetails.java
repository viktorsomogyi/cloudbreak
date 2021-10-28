package com.sequenceiq.cloudbreak.cloud.azure.image;

public class AzureImageCopyDetails {

    public static final AzureImageCopyDetails MARKETPLACE_IMAGE = new AzureImageCopyDetails(true, null, null, null);

    private final boolean marketplaceImage;

    private final String imageStorageName;

    private final String imageResourceGroupName;

    private final String imageSource;

    public AzureImageCopyDetails(String imageStorageName, String imageResourceGroupName, String imageSource) {
        this(false, imageStorageName, imageResourceGroupName, imageSource);
    }

    private AzureImageCopyDetails(boolean marketplaceImage, String imageStorageName, String imageResourceGroupName, String imageSource) {
        this.marketplaceImage = marketplaceImage;
        this.imageStorageName = imageStorageName;
        this.imageResourceGroupName = imageResourceGroupName;
        this.imageSource = imageSource;
    }

    public boolean isMarketplaceImage() {
        return marketplaceImage;
    }

    public String getImageStorageName() {
        return imageStorageName;
    }

    public String getImageResourceGroupName() {
        return imageResourceGroupName;
    }

    public String getImageSource() {
        return imageSource;
    }
}
