var page=0;
var sum=0;
layui.use(['form','layer','laydate','table','laytpl'],function(){
    var form = layui.form,
        layer = parent.layer === undefined ? layui.layer : top.layer,
        $ = layui.jquery,
        laydate = layui.laydate,
        laytpl = layui.laytpl,
        table = layui.table;


    //非管理员登录屏蔽操作按钮
    if(localStorage.getItem("IS_ADMIN")==0){
        $(".userOperationLog_div").empty();
    }

    function createTime(v){
        var date=new Date(v);
        var y=date.getFullYear();
        var m=date.getMonth()+1;
        m=m<10?'0'+m:m;
        var d=date.getDate();
        d=d<10?("0"+d):d;
        var h=date.getHours();
        h=h<10?("0"+h):h;
        var M=date.getMinutes();
        M=M<10?("0"+M):M;
        var s=date.getSeconds();
        s=s<10?("0"+s):s;
        var str=y+"-"+m+"-"+d+" "+h+":"+M+":"+s;
        return str;

    }
    // APP充值列表列表
    var tableIns = table.render({

        elem: '#userOperationLog_table'
        ,url:request("/console/findUserOperationLog")
        ,page: true
        ,curr: 0
        ,limit:Common.limit
        ,limits:Common.limits
        ,groups: 7
        ,cols: [[ //表头
            {field: 'id', title: 'id',sort: true,width:120}
            ,{field: 'nickName', title: '用户昵称',sort: true,width:100}
            ,{field: 'userId', title: '用户Id',sort: true, width:120}
            ,{field: 'telephone', title: '电话号码',sort: true, width:120}
            ,{field: 'ip', title: 'IP地址',sort: true, width:120}
            ,{field: 'area', title: '操作所在地区',sort: true, width:120}
            ,{field: 'actiontime', title: '操作时间',sort: true, width:120,templet : function (d) {
                return createTime(d.actiontime)
                }}
            ,{field: 'province', title: '省份',sort: true, width:120,templet : function (d) {
                    var statusMsg;
                    (d.province == null || '' ? statusMsg = "未知" :  statusMsg = d.province)
                    return statusMsg;
                }}
            ,{field: 'logtype', title: '日志类型',sort: true, width:100, templet : function (d) {
                    var statusMsg;
                    (d.logtype == 1 ? statusMsg = "注册" :(d.logtype == 2) ? statusMsg = "登陆": statusMsg = "其他")
                    return statusMsg;
            }},{field: 'browser', title: '浏览器',sort: true, width:120}
            ,{field: 'operatingSystem', title: '操作系统',sort: true, width:120,templet : function (d) {
                    var statusMsg;
                    (d.operatingSystem == null || '' ? statusMsg = "未知系统" :  statusMsg = d.operatingSystem)
                    return statusMsg;
                }}
        ]]
        ,done:function(res, curr, count){
            checkRequst(res);
            // 初始化时间控件
            ///layui.form.render('select');
            //日期范围
            layui.laydate.render({
                elem: '#userOperationLogMsgDate'
                ,range: "~"
                ,done: function(value, date, endDate){  // choose end
                    //console.log("date callBack====>>>"+value); //得到日期生成的值，如：2017-08-18
                    var startDate = value.split("~")[0];
                    var endDate = value.split("~")[1];


                    // Count.loadGroupMsgCount(roomJId,startDate,endDate,timeUnit);
                    table.reload("userOperationLog_table",{
                        page: {
                            curr: 1 //重新从第 1 页开始
                        },
                        where: {
                            // userId : data.userId,  //搜索的关键字
                            startDate : startDate,
                            endDate : endDate
                        }
                    })
                }
                ,max: 0
            });
            $(".current_total").empty().text((0==res.total ? 0:res.total));
            if(localStorage.getItem("IS_ADMIN")==0){
                $(".btn_addLive").hide();
                $(".delete").hide();
                $(".chatMsg").hide();
                $(".member").hide();
            }
        }
    });

    // 列表操作
    table.on('tool(redEnvelope_table)', function(obj){
        var layEvent = obj.event,
            data = obj.data;
        console.log(data);
        if(layEvent === 'delete'){// 红包领取详情

        }
    });

    //首页搜索
    $(".search_live").on("click",function(){
        // 关闭超出宽度的弹窗
        $(".layui-layer-content").remove();
        table.reload("userOperationLog_table",{
            where: {
                userId : $("#userId").val(), //搜索的关键字
                logtype :$("#logtype_select").val(),
                ip :$("#ip").val(),
                telephone :$("#telephone").val(),
                nickName :$("#nickName").val()
            },
            page: {
                curr: 1 //重新从第 1 页开始
            }
        })
        $("#userId").val("");
        $("#logtype_select").val();
    });


});
var appRecharge={

    // 删除账单记录


    btn_back:function(){
        $("#redEnvelope").show();
        $("#receiveWater").hide();

    }

}