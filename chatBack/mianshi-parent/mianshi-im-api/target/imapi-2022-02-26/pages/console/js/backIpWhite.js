var page=0;
var sum=0;
$(function(){
	Key.backIpWhite_list(0);
	Key.limit();
})



var Key={
	// 黑名单列表
	backIpWhite_list:function(e,pageSize){
		html="";
		if(e==undefined){
			e=0;
		}else if(pageSize==undefined){
			pageSize=Common.limit;
		}
		$.ajax({
			type:'POST',
			url:request('/console/backIpWhiteList'),
			data:{
				pageIndex:(e==0?"0":e-1),
				pageSize:pageSize
			},
			dataType:'json',
			async:false,
			success:function(result){
				checkRequst(result);
				if(result.data.pageData.length!=0){
					$("#pageCount").val(result.data.allPageCount);

					for(var i=0;i<result.data.pageData.length;i++){
						html+="<tr align='center'><td>"+result.data.pageData[i].ip
							+"</td><td>"+UI.getLocalTime(result.data.pageData[i].createTime)+"</td></tr>"
						// <td><button onclick='Key.deleteBackIpWhite(\""+result.data.pageData[i].id+"\")' class='layui-btn layui-btn-danger layui-btn-xs delete'>删除</button></td>
					}

					if($("#ipName").val()==""||$("#ipName").val()==undefined){
						$("#backIpWhiteList_table").empty();
						$("#backIpWhiteList_table").append(html);
					}
					if(localStorage.getItem("role")==1){
						$(".btn_add").hide();
						$(".delete").hide();
					}
					$("#ipName").val("");
					$("#backIpWhiteList").show();
					$("#addBackIpWhite").hide();
					// $("#back").empty();
					// $("#back").append("&nbsp;");

				}
			}
		})
	},
	// 搜索关键词
	findIpBlack:function(){
		html="";
		Common.invoke({
			url:request('/console/backIpWhiteList'),
			data:{
				ip:$("#ipName").val()
			},
			success:function(result){
				if(result.data.pageData!=null){
					$("#pageCount").val(result.data.allPageCount);
					for(var i=0;i<result.data.pageData.length;i++){
						html+="<tr align='center'><td>"+result.data.pageData[i].ip
							+"</td><td>"+UI.getLocalTime(result.data.pageData[i].createTime)+"</td></tr>"
					}
					$("#backIpWhiteList_table").empty();
					$("#backIpWhiteList_table").append(html);
					// $("#ipName").val("");
					Key.limit(1);
					// $("#back").empty();
					// $("#back").append("&nbsp;");
				}
			}
		});
	},
	// 新增敏感词
	addBackIpWhite:function(){
		$("#backIpWhiteList").hide();
		$("#addBackIpWhite").show();
		// $("#back").empty();
		// $("#back").append(button);

	},
	// 提交新增敏感词
	commit_ipBlack:function(){
		$.ajax({
			type:'POST',
			url:request('/console/addBackIpWhite'),
			data:{
				ip:$("#addKeyValue").val()
			},
			async:false,
			success:function(result){
				checkRequst(result);
				if(result.resultCode==1){
					layer.msg("新增成功",{"icon":1});
					Key.backIpWhite_list();
					Key.limit();
					$("#backIpWhiteList").show();
					$("#addBackIpWhite").hide();
					$("#addKeyValue").val("");
				}else{
					layer.msg("操作成功",{"icon":1});
				}
			}
		})
	},
	// 删除敏感词
	deleteBackIpWhite:function(id){
		layer.confirm('确定删除该ip白名单？',{icon:3, title:'提示信息'},function(index){
			Common.invoke({
				url:request('/console/deleteBackIpWhite'),
				data:{
					id:id
				},
				success:function(result){
					if(result.resultCode==1){
						layer.msg("删除成功",{"icon":1});
						Key.backIpWhite_list();
						Key.limit();
					}
				}
			})
		});

	},
	// 分页
	limit:function(index){
		layui.use('laypage', function(){
			var laypage = layui.laypage;
			console.log($("#pageCount").val());
			var count=$("#pageCount").val();
			//执行一个laypage实例
			laypage.render({
				elem: 'laypage'
				,count: count
				,limit:Common.limit
				,limits:Common.limits
				,layout: ['count', 'prev', 'page', 'next', 'limit', 'refresh', 'skip']
				,jump: function(obj){
					console.log(obj)
					if(index==1){
						Key.backIpWhite_list(1,obj.limit)
						index=0;
					}else{
						Key.backIpWhite_list(obj.curr,obj.limit)
					}

				}
			})
		})
	}
}