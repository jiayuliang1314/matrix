package sample.tencent.matrix.zp.data;

public class IssuesTagNum {
    private String tag;
    private int num;

    public IssuesTagNum(String tag, int num) {
        this.tag = tag;
        this.num = num;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public String toString() {
        return "IssuesTagNum{" +
                "tag='" + tag + '\'' +
                ", num=" + num +
                '}';
    }
}
