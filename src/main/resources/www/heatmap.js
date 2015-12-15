 function Heatmap(selection, option) {
        
        var canvasses = initHtml(selection); 
        var cnv = canvasses[0], controll_cnv = canvasses[1], tooltip = canvasses[2];
        var context = cnv.getContext('2d');
        var addCtx = controll_cnv.getContext('2d');
        var cnvSize  = cnv.height;
        var cells = [];
        var cellSize = -1;
        var values;
        var maxValue = -1;
        var mouseStart;
        var mouseEnd;
        var zoomStartX;
        var zoomStartY;
        var cellsIndexes = {};
        var lastSelectedKey;
        
        var self = this;
        
        function initHtml(holderSelection) {
            var oSize = option.size || 100;
            var holder = document.querySelector(holderSelection);
            var cnv = document.createElement("canvas");
            cnv.style.zIndex = 0;
            cnv.width = oSize;
            cnv.height = oSize;
            
            var cnv2 = document.createElement("canvas");
            cnv2.style.zIndex = 1;
            cnv2.style.position = "relative";
            cnv2.style.top = -oSize;
            cnv2.width = oSize;
            cnv2.height = oSize;
            
            var tp = document.createElement("div");
            tp.style.display = "none";
            tp.style.position = "absolute";
            tp.style.boxShadow = "1px 1px 10px #888888";
            tp.parentElement = holder;

            holder.appendChild(cnv);
            holder.appendChild(cnv2);
            holder.appendChild(tp);
            
            return [cnv, cnv2, tp];
        }
        
        function getMousePos(canvas, evt) {
            var rect = canvas.getBoundingClientRect();
            return {
                x: evt.clientX - rect.left,
                y: evt.clientY - rect.top
            };
        }
        
        controll_cnv.addEventListener('mousedown', function(evt) {
            mouseStart = getMousePos(controll_cnv, evt);
        }, true);
        
        controll_cnv.addEventListener('mousemove', function(evt) {
            var mousePos = getMousePos(controll_cnv, evt);
            if (!!mouseStart) {
            
                addCtx.clearRect(0, 0, controll_cnv.width, controll_cnv.height);
                addCtx.fillStyle = "rgba(0, 0, 0, 0.3)";
                addCtx.fillRect(0, 0, mouseStart.x, controll_cnv.height);
                addCtx.fillRect(mouseStart.x, 0, Math.abs(mousePos.x - mouseStart.x), mouseStart.y);
                addCtx.fillRect(mousePos.x, 0, controll_cnv.width - mouseStart.x, controll_cnv.height);
                addCtx.fillRect(mouseStart.x, mousePos.y, Math.abs(mousePos.x - mouseStart.x), controll_cnv.height - mousePos.y);
            
                mouseEnd = mousePos;
            } else {
                var xi = Math.floor(mousePos.x / cellSize);
                var yi = Math.floor((cnvSize - mousePos.y) / cellSize);
                var xIndex = zoomStartX + xi;
                var yIndex = zoomStartY + yi;
                
                var key = cells[xIndex] + "_" + cells[yIndex];
                if ( key != lastSelectedKey ) {
                    lastSelectedKey = key;
                    var value = values[key];
                    if ( !!value ) {
                       // console.log(value);
                        addCtx.clearRect(0, 0, controll_cnv.width, controll_cnv.height);
                        var xc = xi * cellSize;
                        var yc = cnvSize - (yi + 1) * cellSize;
                    
                        addCtx.fillStyle = "rgba(255, 255, 255, 0.5)";
                        addCtx.fillRect(xc, yc, cellSize, cellSize);
                        showTooltip(key, value, controll_cnv.width - xc, yc);
                    }
                }
                
            }
        }, true);
        
        controll_cnv.addEventListener('mouseup', function(evt) {
            addCtx.clearRect(0, 0, controll_cnv.width, controll_cnv.height);
            
            var mousePos = getMousePos(controll_cnv, evt);
            var dx = Math.abs(mousePos.x - mouseStart.x);
            var dy = Math.abs(mousePos.y - mouseStart.y);
            var mod = Math.pow( dx * dx + dy * dy, 0.5 );
            if ( mod > cellSize ) {
                self.zoom(mouseStart, mouseEnd);     
            }
            mouseStart = undefined;   
        }, true);
        
        controll_cnv.addEventListener('mouseout', function(evt) {
            hideToolTip();
            if (!!mouseStart) {
                addCtx.clearRect(0, 0, controll_cnv.width, controll_cnv.height);
                self.zoom(mouseStart, mouseEnd);    
                mouseStart = undefined;    
            }
        }, true);
        
        controll_cnv.addEventListener('dblclick', function(evt) {
            self.zoomOut();
        }, false);
        
        controll_cnv.addEventListener('click', function(evt) {
            if ( !!option.onclick ) {
                var mousePos = getMousePos(controll_cnv, evt);
                var xi = Math.floor(mousePos.x / cellSize);
                var yi = Math.floor((cnvSize - mousePos.y) / cellSize);
                var xIndex = zoomStartX + xi;
                var yIndex = zoomStartY + yi;
                
                option.onclick(cells[xIndex], cells[yIndex]);
            }
        }, true);
        
        //function 
        
        function showTooltip(key, value, x, y) {
            tooltip.style.top = Math.floor(y);
            tooltip.style.right = Math.floor(x + 20);
            tooltip.style.background = "#ffffff";
            tooltip.style.display = "inline";
            key = key.replace("_", " &rarr; ")
            tooltip.innerHTML = "<span style=' font-size: 14px; background: #ffffff; padding: 3px 10px 3px 10px;'>" + key + "</span><span style='background: #4d4d4d; padding: 3px 10px 3px 10px; color: #f2f2f2; font-size: 14px'>" + value + " ms </span>";
        }
        
        function hideToolTip() {
            tooltip.style.display = "none";
        }
        
        function toKey(from, to) {
            var iFrom = cellsIndexes[from];
            var iTo = cellsIndexes[to];
            if (!iFrom || !iTo) {
                return;
            }
            if ( iFrom < iTo ) {
                return to + "_" + from;
            }
            return from + "_" + to;
            //return from + "_" + to;
        }
        
        this.updateGrid = function(data) {
            if ( data.toString() == cells.toString() ) {
                return;
            }
            cells = data;
            cellsIndexes = data.reduce(function(acc, item, index) { acc[item] = index; return acc; }, {});
            cellSize = cnvSize / data.length;
            zoomStartX = 0;
            zoomStartY = 0;
        }
        
        this.updateValues = function(val) {
            maxValue = val.reduce(function(acc, item){ return acc < item.value ? item.value : acc }, -1);
            values = val.reduce(function(acc, item) {
                var key = toKey(item.from, item.to);
                if (!!key ) {
                    acc[key] = item.value;
                }
                return acc;
            }, {});
        }
        
        this.update = function(nodes, edges) {
            if ( !!nodes ) {
                this.updateGrid(nodes);
            }
            
            if ( !!edges ) {
                this.updateValues(edges);
            }
            
            this.plot();
        }
        
        this.zoomOut = function() {
            cellSize = cnvSize / cells.length;
            zoomStartX = 0;
            zoomStartY = 0;
        }
        
        this.zoom = function(start, end) {
            var dx = Math.abs(start.x - end.x);
            var dy = Math.abs(start.y - end.y);
            
            zoomStartX += Math.floor(start.x / cellSize );
            zoomStartY += Math.floor((cnvSize - end.y) / cellSize);
            
            var cellsCount = Math.max(dx, dy) / cellSize;
            cellSize = cnvSize / cellsCount;
            this.plot();
        }
        
        this.plot = function() {
            context.clearRect(0, 0, cnv.width, cnv.height);
            var end = cnvSize / cellSize;
            for (var y = 0; y < end; y++) {
                for (var x = 0; x < end; x++) {
                    
                    var xi = x + zoomStartX;
                    var yi = y + zoomStartY;
    
                    var key = cells[xi] + "_" + cells[yi];
                    var value = values[key];
                    if (!value) continue;
                    
                    var xc = x * cellSize;
                    var yc = cnvSize - (y + 1) * cellSize;
                    context.fillStyle = gradient([21, 171, 226], [242, 242, 242], value / maxValue);
                    context.fillRect(xc, yc, cellSize, cellSize);
                }
            }  
        }
        
        function gradient(firstColor, secondColor, percentage) {
            var result = [];
            for (var i = 0; i < 3; i++) {
                result[i] = Math.floor(firstColor[i] * percentage + secondColor[i] * (1 - percentage));
            }
            return "rgb(" + result[0] + "," + result[1] + "," + result[2] + ")";
        }
        
    }