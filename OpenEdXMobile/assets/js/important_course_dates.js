var className = 'date-summary-container';
var text = '';
var divs = document.getElementsByClassName(className);
for (i = 0; i < divs.length; i++) {
    text += divs[i].outerHTML;
}
document.getElementsByTagName('body')[0].innerHTML = text;
var style = document.createElement('style');
style.innerHTML = 'body { padding-left: 20px; padding-top: 30px; padding-right: 0px }';
document.head.appendChild(style);
document.body.style.backgroundColor = 'white';
document.getElementsByTagName('BODY')[0].style.minHeight = 'auto';
document.title = '';
