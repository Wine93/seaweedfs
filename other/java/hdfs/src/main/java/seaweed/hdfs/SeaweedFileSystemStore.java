package seaweed.hdfs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seaweedfs.client.FilerGrpcClient;
import seaweedfs.client.FilerProto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SeaweedFileSystemStore {

    private static final Logger LOG = LoggerFactory.getLogger(SeaweedFileSystemStore.class);

    private FilerGrpcClient filerGrpcClient;

    public SeaweedFileSystemStore(String host, int port) {
        filerGrpcClient = new FilerGrpcClient(host, port);
    }

    public boolean createDirectory(final Path path, UserGroupInformation currentUser,
                                   final FsPermission permission, final FsPermission umask) {

        LOG.debug("createDirectory path: {} permission: {} umask: {}",
            path,
            permission,
            umask);

        long now = System.currentTimeMillis() / 1000L;

        FilerProto.CreateEntryRequest.Builder request = FilerProto.CreateEntryRequest.newBuilder()
            .setDirectory(path.getParent().toUri().getPath())
            .setEntry(FilerProto.Entry.newBuilder()
                .setName(path.getName())
                .setIsDirectory(true)
                .setAttributes(FilerProto.FuseAttributes.newBuilder()
                    .setMtime(now)
                    .setCrtime(now)
                    .setFileMode(permission.toShort())
                    .setUserName(currentUser.getUserName())
                    .addAllGroupName(Arrays.asList(currentUser.getGroupNames())))
            );

        FilerProto.CreateEntryResponse response = filerGrpcClient.getBlockingStub().createEntry(request.build());
        return true;
    }

    public FileStatus[] listEntries(final Path path) {
        LOG.debug("listEntries path: {}", path);

        List<FileStatus> fileStatuses = new ArrayList<FileStatus>();

        FilerProto.ListEntriesResponse response =
            filerGrpcClient.getBlockingStub().listEntries(FilerProto.ListEntriesRequest.newBuilder()
                .setDirectory(path.toUri().getPath())
                .setLimit(100000)
                .build());

        for (FilerProto.Entry entry : response.getEntriesList()) {

            FileStatus fileStatus = getFileStatus(new Path(path, entry.getName()), entry);

            fileStatuses.add(fileStatus);
        }
        return fileStatuses.toArray(new FileStatus[0]);
    }

    public FileStatus getFileStatus(final Path path) {
        LOG.debug("getFileStatus path: {}", path);

        FilerProto.LookupDirectoryEntryResponse response =
            filerGrpcClient.getBlockingStub().lookupDirectoryEntry(FilerProto.LookupDirectoryEntryRequest.newBuilder()
                .setDirectory(path.getParent().toUri().getPath())
                .setName(path.getName())
                .build());

        FilerProto.Entry entry = response.getEntry();
        FileStatus fileStatus = getFileStatus(path, entry);
        return fileStatus;
    }

    public boolean deleteEntries(final Path path, boolean isDirectroy, boolean recursive) {
        LOG.debug("deleteEntries path: {} isDirectory {} recursive: {}",
            path,
            String.valueOf(isDirectroy),
            String.valueOf(recursive));

        FilerProto.DeleteEntryResponse response =
            filerGrpcClient.getBlockingStub().deleteEntry(FilerProto.DeleteEntryRequest.newBuilder()
                .setDirectory(path.getParent().toUri().getPath())
                .setName(path.getName())
                .setIsDirectory(isDirectroy)
                .setIsDeleteData(true)
                .build());

        return true;
    }


    private FileStatus getFileStatus(Path path, FilerProto.Entry entry) {
        FilerProto.FuseAttributes attributes = entry.getAttributes();
        long length = attributes.getFileSize();
        boolean isDir = entry.getIsDirectory();
        int block_replication = 1;
        int blocksize = 512;
        long modification_time = attributes.getMtime();
        long access_time = 0;
        FsPermission permission = FsPermission.createImmutable((short) attributes.getFileMode());
        String owner = attributes.getUserName();
        String group = attributes.getGroupNameCount() > 0 ? attributes.getGroupName(0) : "";
        return new FileStatus(length, isDir, block_replication, blocksize,
            modification_time, access_time, permission, owner, group, null, path);
    }

}