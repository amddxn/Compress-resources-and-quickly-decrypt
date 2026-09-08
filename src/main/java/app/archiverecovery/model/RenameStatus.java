package app.archiverecovery.model;

public enum RenameStatus {
    READY("等待修改"),
    UNCHANGED("保持不变"),
    CONFLICT("名称冲突"),
    SUCCESS("修改成功"),
    FAILED("修改失败");

    private final String displayName;

    RenameStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
