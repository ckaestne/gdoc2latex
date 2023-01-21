const structure = require('../structure.js')

 
module.exports = {
	title: "Software Engineering for AI-Enabled Systems",
	//base: "file:///home/ckaestne/Dropbox/work/code/gdoc2latex/book_web/.vuepress/dist/",
  	themeConfig: {
		lastUpdated: 'Last Updated',
		repo: 'ckaestne/seaibook',
		editLinks: true,docsBranch:'main', editLinkText: 'Contribute to this chapter',
	  	displayAllHeaders: false,
    	nav: [
			{ text: 'Home', link: '/' },
//			{ text: 'GitHub', link: 'https://github.com/ckaestne/seaibook' },
			{ text: 'Lecture', link: 'https://ckaestne.github.io/seai/' },
			{ text: 'Videos', link: 'https://www.youtube.com/watch?v=Wst5A6ZB7Bg&list=PLDS2JMJnJzdkQPdkhcuwcbJpjB84g9ffX' },
    	],
    	sidebar: structure
	},
	plugins: ['@vuepress/active-header-links', '@vuepress/medium-zoom', 'flexsearch'],
	markdown: {
	    plugins: {
	      'markdown-it-implicit-figures': {
	      	figcaption: true
	      }
	    }
  }
}
// console.log(JSON.stringify(module.exports))
